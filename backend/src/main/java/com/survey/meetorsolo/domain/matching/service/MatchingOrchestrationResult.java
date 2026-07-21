package com.survey.meetorsolo.domain.matching.service;

import java.util.List;

public record MatchingOrchestrationResult(
        String lockToken,
        int claimedCount,
        List<Long> createdAttemptIds,
        int failedGroupCount,
        int releasedCount
) {
    public MatchingOrchestrationResult { createdAttemptIds = List.copyOf(createdAttemptIds); }
}
