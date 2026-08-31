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
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
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
    private final FestivalCheckinProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public FestivalCheckinService(
            FestivalRepository festivalRepository,
            FestivalCheckinRepository festivalCheckinRepository,
            FestivalCheckinProperties properties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.festivalRepository = festivalRepository;
        this.festivalCheckinRepository = festivalCheckinRepository;
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

        boolean bypassRadiusCheck = properties.bypassRadiusCheck();
        if (!bypassRadiusCheck
                && request.accuracyMeters() != null
                && request.accuracyMeters() > properties.accuracyThresholdMeters()) {
            throw new BusinessException(ErrorCode.LOW_LOCATION_ACCURACY);
        }

        long distanceMeters = GeoDistanceCalculator.metersBetween(
                festival.getMapY(),
                festival.getMapX(),
                request.latitude(),
                request.longitude()
        );
        if (!bypassRadiusCheck && distanceMeters > festival.getCheckinRadiusMeters()) {
            throw new BusinessException(ErrorCode.CHECKIN_OUT_OF_RANGE);
        }
        if (bypassRadiusCheck && distanceMeters > festival.getCheckinRadiusMeters()) {
            log.warn(
                    "GPS 반경 검증을 건너뛰고 체크인을 허용했습니다(local/dev 전용). "
                            + "memberId={}, festivalId={}, distanceMeters={}, radiusMeters={}",
                    memberId, festivalId, distanceMeters, festival.getCheckinRadiusMeters()
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
