package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository.ActiveGroupWithFestivalProjection;
import java.math.BigDecimal;

public record MatchGroupMeetingPointResponse(
        String name, String address, String contentId, BigDecimal longitude, BigDecimal latitude,
        Integer candidateSearchRadiusMeters, Integer arrivalRadiusMeters
) {
    public static final int ARRIVAL_RADIUS_METERS = 150;

    public static MatchGroupMeetingPointResponse from(ActiveGroupWithFestivalProjection group) {
        if (group.getMeetingPlaceName() == null || group.getMeetingPlaceAddress() == null
                || group.getMeetingPlaceContentId() == null
                || group.getMeetingMapX() == null || group.getMeetingMapY() == null) return null;
        return new MatchGroupMeetingPointResponse(group.getMeetingPlaceName(),
                group.getMeetingPlaceAddress(), group.getMeetingPlaceContentId(),
                group.getMeetingMapX(), group.getMeetingMapY(), group.getMeetingRadiusMeters(),
                ARRIVAL_RADIUS_METERS);
    }
}
