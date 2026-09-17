package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.checkin.CheckinValidityPolicy;
import com.survey.meetorsolo.domain.festival.config.FestivalCheckinProperties;
import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.CurrentCheckinResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckin;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.event.FestivalCheckinCancelledEvent;
import com.survey.meetorsolo.domain.festival.repository.FestivalCheckinRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.geo.DistanceText;
import com.survey.meetorsolo.global.geo.GeoDistanceCalculator;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalCheckinService {

    private static final Logger log = LoggerFactory.getLogger(FestivalCheckinService.class);

    private final FestivalRepository festivalRepository;
    private final FestivalCheckinRepository festivalCheckinRepository;
    private final MemberRepository memberRepository;
    private final FestivalCheckinProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public FestivalCheckinService(
            FestivalRepository festivalRepository,
            FestivalCheckinRepository festivalCheckinRepository,
            MemberRepository memberRepository,
            FestivalCheckinProperties properties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.festivalRepository = festivalRepository;
        this.festivalCheckinRepository = festivalCheckinRepository;
        this.memberRepository = memberRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FestivalCheckinResponse checkIn(Long memberId, Long festivalId, CheckInRequest request) {
        Festival festival = festivalRepository.findById(festivalId)
                .filter(found -> found.getStatus() == FestivalStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "축제를 찾을 수 없습니다."));

        if (festival.getMapX() == null || festival.getMapY() == null) {
            throw new BusinessException(ErrorCode.FESTIVAL_LOCATION_UNAVAILABLE);
        }

        // 반경 검증을 건너뛰는 경로는 두 가지이고 의미가 다르다.
        // - 설정(bypassRadiusCheck): 환경 전체를 끈다. 실기기 GPS가 없는 환경의 임시 수단이다.
        // - 테스트 계정: 그 계정만 면제한다. 같은 환경에서 일반 계정은 반경 검증을 그대로 받는다.
        // 둘을 나눠 두면 dev 환경에서도 "반경 검증이 실제로 동작하는지"를 확인할 수 있다.
        boolean testAccount = memberRepository.existsByIdAndTestAccountIsTrue(memberId);
        boolean bypassRadiusCheck = properties.bypassRadiusCheck() || testAccount;

        long distanceMeters = GeoDistanceCalculator.metersBetween(
                festival.getMapY(),
                festival.getMapX(),
                request.latitude(),
                request.longitude()
        );

        // radiusMeters는 festivals.checkin_radius_meters(DB 컬럼)가 아니라 설정값이다.
        // 그 컬럼을 축제마다 다르게 채우는 코드가 없어 모든 축제가 항상 같은 값이었고, 그래서
        // 축제별 값이 아니라 환경변수(app.festival.checkin.radius-meters)로 조절하는 전역
        // 값으로 뒀다(docs/32 3.1 후속).
        int radiusMeters = properties.radiusMeters();

        // 거리를 정확도보다 먼저 본다. 순서를 뒤집으면 실제 원인이 "현장에 없다"인 사람에게도
        // "정확도가 낮다"가 뜬다. 실내·PC 측위는 정확도가 수백 m로 나오는 일이 흔해서, 반경 밖에
        // 있는 사람은 사실상 항상 정확도 오류만 보게 되고 같은 자리에서 다시 누른다.
        // 정확도 검사는 "반경 안에 있다고 판정된 좌표를 믿어도 되는가"를 보는 것이므로
        // 거리 판정 뒤에 오는 것이 의미상으로도 맞다(docs/32 3.1).
        if (!bypassRadiusCheck) {
            if (distanceMeters > radiusMeters) {
                throw new BusinessException(
                        ErrorCode.CHECKIN_OUT_OF_RANGE,
                        outOfRangeMessage(distanceMeters, radiusMeters));
            }
            if (request.accuracyMeters() != null
                    && request.accuracyMeters() > properties.accuracyThresholdMeters()) {
                throw new BusinessException(ErrorCode.LOW_LOCATION_ACCURACY);
            }
        }
        if (bypassRadiusCheck && distanceMeters > radiusMeters) {
            log.warn(
                    "GPS 반경 검증을 건너뛰고 체크인을 허용했습니다(사유={}). "
                            + "memberId={}, festivalId={}, distanceMeters={}, radiusMeters={}",
                    testAccount ? "TEST_ACCOUNT" : "CONFIG_BYPASS",
                    memberId, festivalId, distanceMeters, radiusMeters
            );
        }

        // 한 사람이 동시에 여러 곳에 있을 수 없으므로, 새 체크인 시 같은 회원의 기존 ACTIVE
        // 체크인(다른 축제 포함)은 전부 취소한다.
        // 취소된 축제에 이 회원의 활성 match_pool이 남아있을 수 있으므로, matching 도메인이
        // 정리할 수 있게 축제별로 FestivalCheckinCancelledEvent를 발행한다(1단계: 이벤트 발행만.
        // WAITING 풀 정리는 matching 도메인의 구독 핸들러가 담당 —
        // docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md 참고).
        List<FestivalCheckin> existingActive =
                festivalCheckinRepository.findAllByMemberIdAndStatus(memberId, FestivalCheckinStatus.ACTIVE);
        cancelAndPublish(memberId, existingActive);

        FestivalCheckin checkin = FestivalCheckin.create(
                memberId,
                festivalId,
                Math.toIntExact(distanceMeters),
                CheckinValidityPolicy.VALIDITY
        );
        festivalCheckinRepository.save(checkin);

        return new FestivalCheckinResponse(
                checkin.getId(),
                checkin.getFestivalId(),
                checkin.getDistanceMeters(),
                checkin.getStatus(),
                checkin.getCheckedInAt(),
                checkin.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<CurrentCheckinResponse> getCurrentCheckin(Long memberId) {
        OffsetDateTime now = SeoulDateTime.now();
        return festivalCheckinRepository.findValidActiveCheckin(memberId, now)
                .map(checkin -> new CurrentCheckinResponse(
                        checkin.getId(),
                        checkin.getFestivalId(),
                        festivalRepository.findById(checkin.getFestivalId())
                                .map(Festival::getTitle)
                                .orElse(null),
                        checkin.getCheckedInAt(),
                        checkin.getExpiresAt()
                ));
    }

    /** 매칭 신청 전(IDLE) 화면에서 사용자가 직접 누르는 체크인 취소. 활성 체크인이 없으면 거절한다. */
    @Transactional
    public void cancelCurrentCheckin(Long memberId) {
        List<FestivalCheckin> existingActive =
                festivalCheckinRepository.findAllByMemberIdAndStatus(memberId, FestivalCheckinStatus.ACTIVE);
        if (existingActive.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "활성 체크인이 없습니다.");
        }
        cancelAndPublish(memberId, existingActive);
    }

    /**
     * 탈퇴 시 활성 체크인을 정리한다.
     *
     * <p>{@link #cancelCurrentCheckin}과 달리 활성 체크인이 없어도 예외를 던지지 않는다.
     * 탈퇴는 어떤 상태에서도 실패하지 않아야 하고 반복 호출이 멱등해야 한다.
     */
    @Transactional
    public void cancelAllOnWithdrawal(Long memberId) {
        cancelAndPublish(memberId, festivalCheckinRepository.findAllByMemberIdAndStatus(
                memberId, FestivalCheckinStatus.ACTIVE));
    }

    /**
     * 반경 밖 거절 문구를 만든다. 거리를 함께 알려 주는 이유는 사용자가 "GPS가 이상한 것"과
     * "내가 정말 먼 것"을 구분할 수 있어야 하기 때문이다.
     *
     * <p>좌표는 저장하지 않지만 거리는 이미 성공 응답({@code distanceMeters})으로 내보내고 있어
     * 노출 범위가 넓어지지 않는다.
     */
    private static String outOfRangeMessage(long distanceMeters, int radiusMeters) {
        return "축제에서 약 %s 떨어져 있어요. 체크인은 축제 반경 %s 안에서 할 수 있어요."
                .formatted(DistanceText.of(distanceMeters), DistanceText.of(radiusMeters));
    }

    /**
     * 취소 UPDATE를 반영하고 matching 도메인이 WAITING pool을 정리할 수 있도록
     * 축제별로 {@link FestivalCheckinCancelledEvent}를 발행한다. 같은 축제로 재체크인하는
     * 경우 취소 UPDATE가 새 ACTIVE INSERT보다 먼저 DB에 반영돼야
     * uq_festival_checkins_member_festival_active(부분 unique index)를 위반하지 않으므로
     * Hibernate의 기본 flush 순서(INSERT 우선) 대신 명시적으로 flush한다.
     */
    private void cancelAndPublish(Long memberId, List<FestivalCheckin> existingActive) {
        if (existingActive.isEmpty()) {
            return;
        }
        existingActive.forEach(FestivalCheckin::cancel);
        festivalCheckinRepository.saveAll(existingActive);
        festivalCheckinRepository.flush();
        OffsetDateTime cancelledAt = SeoulDateTime.now();
        existingActive.forEach(cancelled -> eventPublisher.publishEvent(
                new FestivalCheckinCancelledEvent(memberId, cancelled.getFestivalId(), cancelledAt)));
    }
}
