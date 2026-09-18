package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import java.time.OffsetDateTime;

public record FestivalCheckinResponse(
        Long id,
        Long festivalId,
        int distanceMeters,
        FestivalCheckinStatus status,
        OffsetDateTime checkedInAt,
        OffsetDateTime expiresAt
) {
}
