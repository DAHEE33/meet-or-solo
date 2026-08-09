package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPoint;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FestivalMeetingPointResponse(
        Long id, Long festivalId, String kakaoPlaceId, String name, String address,
        BigDecimal longitude, BigDecimal latitude, FestivalMeetingPointStatus status,
        Integer assignmentOrder, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
    public static FestivalMeetingPointResponse from(FestivalMeetingPoint point) {
        return new FestivalMeetingPointResponse(point.getId(), point.getFestivalId(),
                point.getKakaoPlaceId(), point.getName(), point.getAddress(), point.getMapX(),
                point.getMapY(), point.getStatus(), point.getAssignmentOrder(),
                point.getCreatedAt(), point.getUpdatedAt());
    }
}
