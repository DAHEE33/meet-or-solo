package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository.ActiveGroupWithFestivalProjection;
import java.math.BigDecimal;

/**
 * 만남 장소와 도착 인정 반경이다.
 *
 * <p>{@code arrivalRadiusMeters}는 안내 문구가 아니라 <b>서버가 실제로 검증하는 값</b>이다
 * ({@code docs/19} 4.11.3). 예전에는 이 자리에 상수 150이 박혀 있었고 서버는 좌표를 받지도 않아,
 * 화면에 뜬 반경과 실제 판정이 아무 관계가 없었다. 이제 {@code app.matching.arrival.radius-meters}
 * 한 곳에서 나온 값을 화면과 검증이 함께 쓴다.
 */
public record MatchGroupMeetingPointResponse(
        String name, String address, String contentId, BigDecimal longitude, BigDecimal latitude,
        Integer candidateSearchRadiusMeters, Integer arrivalRadiusMeters
) {

    public static MatchGroupMeetingPointResponse from(
            ActiveGroupWithFestivalProjection group, int arrivalRadiusMeters) {
        if (group.getMeetingPlaceName() == null || group.getMeetingPlaceAddress() == null
                || group.getMeetingPlaceContentId() == null
                || group.getMeetingMapX() == null || group.getMeetingMapY() == null) return null;
        return new MatchGroupMeetingPointResponse(group.getMeetingPlaceName(),
                group.getMeetingPlaceAddress(), group.getMeetingPlaceContentId(),
                group.getMeetingMapX(), group.getMeetingMapY(), group.getMeetingRadiusMeters(),
                arrivalRadiusMeters);
    }
}
