package com.survey.meetorsolo.domain.matching.group;

import java.math.BigDecimal;
import java.util.List;

public record MatchGroupCombination(
        List<MatchingCandidate> candidates,
        BigDecimal score
) {

    public MatchGroupCombination {
        candidates = List.copyOf(candidates);
    }
}
