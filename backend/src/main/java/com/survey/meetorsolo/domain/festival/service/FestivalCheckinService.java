package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.config.FestivalCheckinProperties;
import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckin;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalCheckinRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.geo.GeoDistanceCalculator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FestivalCheckinService {

    private final FestivalRepository festivalRepository;
    private final FestivalCheckinRepository festivalCheckinRepository;
    private final FestivalCheckinProperties properties;

    public FestivalCheckinService(
            FestivalRepository festivalRepository,
            FestivalCheckinRepository festivalCheckinRepository,
            FestivalCheckinProperties properties
    ) {
        this.festivalRepository = festivalRepository;
        this.festivalCheckinRepository = festivalCheckinRepository;
        this.properties = properties;
    }

    @Transactional
    public FestivalCheckinResponse checkIn(Long memberId, Long festivalId, CheckInRequest request) {
        Festival festival = festivalRepository.findById(festivalId)
                .filter(found -> found.getStatus() == FestivalStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "축제를 찾을 수 없습니다."));

        if (festival.getMapX() == null || festival.getMapY() == null) {
            throw new BusinessException(ErrorCode.FESTIVAL_LOCATION_UNAVAILABLE);
        }

        if (request.accuracyMeters() != null && request.accuracyMeters() > properties.accuracyThresholdMeters()) {
            throw new BusinessException(ErrorCode.LOW_LOCATION_ACCURACY);
        }

        long distanceMeters = GeoDistanceCalculator.metersBetween(
                festival.getMapY(),
                festival.getMapX(),
                request.latitude(),
                request.longitude()
        );
        if (distanceMeters > festival.getCheckinRadiusMeters()) {
            throw new BusinessException(ErrorCode.CHECKIN_OUT_OF_RANGE);
        }

        // 한 사람이 동시에 여러 곳에 있을 수 없으므로, 새 체크인 시 같은 회원의 기존 ACTIVE
        // 체크인(다른 축제 포함)은 전부 취소한다.
        // TODO(matching engine): domain/matching이 구현되면 이때 해당 회원의 활성
        // match_pools row도 함께 취소해야 한다. 아직 매칭 엔진 코드가 없어 이번 범위에서는
        // festival_checkins만 정리한다.
        List<FestivalCheckin> existingActive =
                festivalCheckinRepository.findAllByMemberIdAndStatus(memberId, FestivalCheckinStatus.ACTIVE);
        existingActive.forEach(FestivalCheckin::cancel);
        // 같은 축제로 재체크인하는 경우, 취소 UPDATE가 새 ACTIVE INSERT보다 먼저 DB에 반영돼야
        // uq_festival_checkins_member_festival_active(부분 unique index)를 위반하지 않는다.
        // Hibernate의 기본 flush 순서(INSERT 우선)에 맡기면 두 row가 순간적으로 모두 ACTIVE로
        // 보여 제약 위반이 나므로 명시적으로 flush한다.
        if (!existingActive.isEmpty()) {
            festivalCheckinRepository.saveAll(existingActive);
            festivalCheckinRepository.flush();
        }

        FestivalCheckin checkin = FestivalCheckin.create(
                memberId,
                festivalId,
                Math.toIntExact(distanceMeters),
                properties.validDuration()
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
}
