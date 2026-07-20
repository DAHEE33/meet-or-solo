package com.survey.meetorsolo.domain.matching.service;

public record MatchPoolCleanupResult(
        int expiredWaitingCount,
        int expiredStaleLockedCount,
        int releasedStaleLockedCount
) {

    public int totalChangedCount() {
        return expiredWaitingCount + expiredStaleLockedCount + releasedStaleLockedCount;
    }
}
