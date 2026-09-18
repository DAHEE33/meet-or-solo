package com.survey.meetorsolo.domain.matching.service;

import java.time.Duration;
import java.time.OffsetDateTime;

public final class MatchArrivalDeadlinePolicy {

    public static final Duration ARRIVAL_WINDOW = Duration.ofMinutes(30);

    private MatchArrivalDeadlinePolicy() {
    }

    public static OffsetDateTime deadlineAt(OffsetDateTime confirmedAt) {
        return confirmedAt.plus(ARRIVAL_WINDOW);
    }
}
