package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.dto.MatchProposalActionRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchProposalActionResponse;
import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import com.survey.meetorsolo.domain.matching.repository.MatchProposalRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class MatchProposalActionService {

    private final Clock clock;
    private final MatchProposalRepository proposals;
    private final MatchProposalResponseService responses;

    public MatchProposalActionService(
            Clock clock,
            MatchProposalRepository proposals,
            MatchProposalResponseService responses
    ) {
        this.clock = clock;
        this.proposals = proposals;
        this.responses = responses;
    }

    public MatchProposalActionResponse respond(
            long memberId,
            long proposalId,
            MatchProposalActionRequest.Action action
    ) {
        MatchProposal proposal = proposals.findByIdAndMemberId(proposalId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_RESOURCE_NOT_FOUND));
        String serviceResponse = mapAction(proposal, action);
        try {
            MatchProposalResponseResult result = responses.respond(
                    proposalId,
                    memberId,
                    serviceResponse,
                    OffsetDateTime.now(clock)
            );
            return MatchProposalActionResponse.from(action, result);
        } catch (MatchProposalResponseException exception) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "proposal을 현재 상태에서 처리할 수 없습니다.");
        }
    }

    private String mapAction(MatchProposal proposal, MatchProposalActionRequest.Action action) {
        if (MatchProposal.TYPE_INITIAL_MATCH.equals(proposal.getProposalType())) {
            return switch (action) {
                case ACCEPT -> MatchProposal.STATUS_ACCEPTED;
                case REJECT -> MatchProposal.STATUS_REJECTED;
                case CANCEL_CURRENT_MEMBERS -> throw invalidAction();
            };
        }
        if (MatchProposal.TYPE_INSUFFICIENT_MEMBERS_CONFIRMATION.equals(proposal.getProposalType())) {
            return switch (action) {
                case ACCEPT -> "START_WITH_CURRENT_MEMBERS";
                case CANCEL_CURRENT_MEMBERS -> "CANCEL_CURRENT_MEMBERS";
                case REJECT -> throw invalidAction();
            };
        }
        throw new BusinessException(ErrorCode.MATCHING_INVALID_REQUEST);
    }

    private BusinessException invalidAction() {
        return new BusinessException(ErrorCode.MATCHING_INVALID_REQUEST, "proposal 유형에 맞지 않는 action입니다.");
    }
}
