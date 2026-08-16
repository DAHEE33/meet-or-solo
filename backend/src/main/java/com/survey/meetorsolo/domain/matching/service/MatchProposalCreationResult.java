package com.survey.meetorsolo.domain.matching.service;

import java.util.List;

public record MatchProposalCreationResult(long attemptId, List<Long> poolIds) {
    public MatchProposalCreationResult { poolIds = List.copyOf(poolIds); }
}
