package com.survey.meetorsolo.domain.matching.event;

import java.time.OffsetDateTime;
import java.util.List;

public record MatchingStateChangedEvent(
        List<Long> memberIds,
        String reason,
        OffsetDateTime occurredAt
) {

    public MatchingStateChangedEvent {
        memberIds = List.copyOf(memberIds);
    }
}
