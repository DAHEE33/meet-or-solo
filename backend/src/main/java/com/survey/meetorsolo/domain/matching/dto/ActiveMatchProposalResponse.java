package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.entity.MatchAttempt;
import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import java.time.OffsetDateTime;

public record ActiveMatchProposalResponse(
        long proposalId,
        long attemptId,
        String proposalType,
        int proposalRound,
        String status,
        int targetGroupSize,
        String attemptStatus,
        OffsetDateTime expiresAt
) {
    public static ActiveMatchProposalResponse from(MatchProposal proposal, MatchAttempt attempt) {
        return new ActiveMatchProposalResponse(
                proposal.getId(),
                proposal.getAttemptId(),
                proposal.getProposalType(),
                proposal.getProposalRound(),
                proposal.getStatus(),
                attempt.getTargetGroupSize(),
                attempt.getStatus(),
                proposal.getExpiresAt()
        );
    }
}
