package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.service.MatchProposalResponseResult;

public record MatchProposalActionResponse(
        long attemptId,
        long proposalId,
        MatchProposalActionRequest.Action action,
        String recordedResponse,
        String attemptStatus
) {
    public static MatchProposalActionResponse from(
            MatchProposalActionRequest.Action action,
            MatchProposalResponseResult result
    ) {
        return new MatchProposalActionResponse(
                result.attemptId(),
                result.proposalId(),
                action,
                result.response(),
                result.attemptStatus()
        );
    }
}
