package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import com.survey.meetorsolo.domain.matching.entity.MatchPenaltyEvent;
import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import com.survey.meetorsolo.domain.matching.repository.MatchCooldownRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPenaltyEventRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchPenaltyCooldownService {

    private final MatchCooldownRepository cooldowns;
    private final MatchPenaltyEventRepository penaltyEvents;
    private final MemberRepository members;

    public MatchPenaltyCooldownService(
            MatchCooldownRepository cooldowns,
            MatchPenaltyEventRepository penaltyEvents,
            MemberRepository members
    ) {
        this.cooldowns = cooldowns;
        this.penaltyEvents = penaltyEvents;
        this.members = members;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(
            MatchProposal proposal,
            MatchingPenaltyDecision decision,
            OffsetDateTime startsAt
    ) {
        long proposalId = proposal.getId();
        if (cooldowns.existsByRelatedProposalId(proposalId)) {
            return;
        }

        cooldowns.expireElapsedActive(proposal.getMemberId(), startsAt);
        cooldowns.save(MatchCooldown.active(
                proposal.getMemberId(),
                decision.cooldownReason(),
                proposalId,
                startsAt,
                startsAt.plus(decision.cooldownDuration())
        ));

        if (!decision.hasPenaltyScore()) {
            return;
        }
        if (penaltyEvents.existsByRelatedProposalId(proposalId)) {
            return;
        }

        penaltyEvents.save(MatchPenaltyEvent.of(
                proposal.getMemberId(),
                decision.penaltyEventType(),
                decision.scoreDelta(),
                decision.penaltyReason(),
                proposal.getAttemptId(),
                proposalId,
                startsAt
        ));
        int updated = members.increasePenaltyScore(proposal.getMemberId(), decision.scoreDelta());
        if (updated != 1) {
            throw new IllegalStateException("penalty 대상 회원을 갱신할 수 없습니다.");
        }
    }
}
