package com.survey.meetorsolo.domain.matching.service;

import java.util.List;

public record MatchPoolClaimResult(String lockToken, List<Long> poolIds) {

    public MatchPoolClaimResult {
        poolIds = List.copyOf(poolIds);
    }
}
