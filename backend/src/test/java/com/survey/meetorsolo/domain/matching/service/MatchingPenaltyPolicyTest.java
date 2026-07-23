package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MatchingPenaltyPolicyTest {

    private final MatchingPenaltyPolicy policy = new MatchingPenaltyPolicy();

    @Test
    void round1_거절은_30초_cooldown만_적용한다() {
        MatchingPenaltyDecision decision = policy.roundOneRejected();

        assertThat(decision.cooldownReason()).isEqualTo("REJECT");
        assertThat(decision.cooldownDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(decision.hasPenaltyScore()).isFalse();
    }

    @Test
    void round1_timeout은_2분_cooldown과_1점을_적용한다() {
        MatchingPenaltyDecision decision = policy.roundOneTimeout();

        assertThat(decision.cooldownReason()).isEqualTo("TIMEOUT");
        assertThat(decision.cooldownDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(decision.penaltyEventType()).isEqualTo("TIMEOUT");
        assertThat(decision.scoreDelta()).isOne();
    }

    @Test
    void round2_취소는_2분_cooldown과_1점을_적용한다() {
        MatchingPenaltyDecision decision = policy.roundTwoCancelled();

        assertThat(decision.cooldownReason()).isEqualTo("CANCEL");
        assertThat(decision.cooldownDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(decision.penaltyEventType()).isEqualTo("CANCEL");
        assertThat(decision.scoreDelta()).isOne();
    }

    @Test
    void round2_timeout은_5분_cooldown과_2점을_적용한다() {
        MatchingPenaltyDecision decision = policy.roundTwoTimeout();

        assertThat(decision.cooldownReason()).isEqualTo("TIMEOUT");
        assertThat(decision.cooldownDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(decision.penaltyEventType()).isEqualTo("TIMEOUT");
        assertThat(decision.scoreDelta()).isEqualTo(2);
    }
}
