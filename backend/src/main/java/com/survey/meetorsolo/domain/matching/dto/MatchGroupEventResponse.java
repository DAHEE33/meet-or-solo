package com.survey.meetorsolo.domain.matching.dto;

import java.time.OffsetDateTime;

public record MatchGroupEventResponse(
        long eventId,
        String type,
        OffsetDateTime occurredAt,
        MatchEventActorResponse actor,
        Integer arrivalMinutes
) {
}
