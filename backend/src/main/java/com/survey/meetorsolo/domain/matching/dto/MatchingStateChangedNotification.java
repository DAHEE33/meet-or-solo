package com.survey.meetorsolo.domain.matching.dto;

import java.time.OffsetDateTime;

public record MatchingStateChangedNotification(
        String type,
        String reason,
        OffsetDateTime occurredAt
) {

    public static MatchingStateChangedNotification of(String reason, OffsetDateTime occurredAt) {
        return new MatchingStateChangedNotification("MATCHING_STATE_CHANGED", reason, occurredAt);
    }
}
