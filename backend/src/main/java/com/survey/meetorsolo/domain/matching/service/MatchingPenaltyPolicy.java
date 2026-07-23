package com.survey.meetorsolo.domain.matching.service;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MatchingPenaltyPolicy {

    public MatchingPenaltyDecision roundOneRejected() {
        return new MatchingPenaltyDecision(
                "REJECT", Duration.ofSeconds(30), null, 0, null);
    }

    public MatchingPenaltyDecision roundOneTimeout() {
        return new MatchingPenaltyDecision(
                "TIMEOUT", Duration.ofMinutes(2), "TIMEOUT", 1, "ROUND_1_TIMEOUT");
    }

    public MatchingPenaltyDecision roundTwoCancelled() {
        return new MatchingPenaltyDecision(
                "CANCEL", Duration.ofMinutes(2), "CANCEL", 1, "ROUND_2_CANCEL");
    }

    public MatchingPenaltyDecision roundTwoTimeout() {
        return new MatchingPenaltyDecision(
                "TIMEOUT", Duration.ofMinutes(5), "TIMEOUT", 2, "ROUND_2_TIMEOUT");
    }
}
