package com.survey.meetorsolo.domain.matching.service;

import java.time.Duration;

public record MatchingPenaltyDecision(
        String cooldownReason,
        Duration cooldownDuration,
        String penaltyEventType,
        int scoreDelta,
        String penaltyReason
) {
    public MatchingPenaltyDecision {
        if (cooldownReason == null || cooldownReason.isBlank()) {
            throw new IllegalArgumentException("cooldownReason은 필수입니다.");
        }
        if (cooldownDuration == null || cooldownDuration.isZero() || cooldownDuration.isNegative()) {
            throw new IllegalArgumentException("cooldownDuration은 양수여야 합니다.");
        }
        if (scoreDelta < 0) {
            throw new IllegalArgumentException("scoreDelta는 음수일 수 없습니다.");
        }
        if (scoreDelta > 0 && (penaltyEventType == null || penaltyEventType.isBlank())) {
            throw new IllegalArgumentException("점수 penalty에는 event type이 필요합니다.");
        }
    }

    public boolean hasPenaltyScore() {
        return scoreDelta > 0;
    }
}
