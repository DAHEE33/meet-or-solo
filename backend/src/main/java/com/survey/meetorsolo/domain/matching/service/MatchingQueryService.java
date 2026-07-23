package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.dto.ActiveMatchProposalResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchingRestrictionResponse;
import com.survey.meetorsolo.domain.matching.entity.MatchAttempt;
import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import com.survey.meetorsolo.domain.matching.repository.MatchAttemptRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchCooldownRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchProposalRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MatchingQueryService {

    private final Clock clock;
    private final MatchPoolRepository pools;
    private final MatchProposalRepository proposals;
    private final MatchAttemptRepository attempts;
    private final MatchCooldownRepository cooldowns;
    private final MemberRepository members;

    public MatchingQueryService(
            Clock clock,
            MatchPoolRepository pools,
            MatchProposalRepository proposals,
            MatchAttemptRepository attempts,
            MatchCooldownRepository cooldowns,
            MemberRepository members
    ) {
        this.clock = clock;
        this.pools = pools;
        this.proposals = proposals;
        this.attempts = attempts;
        this.cooldowns = cooldowns;
        this.members = members;
    }

    public MatchPoolResponse currentPool(long memberId) {
        requireMember(memberId);
        return pools.findFirstByMemberIdOrderByIdDesc(memberId)
                .map(MatchPoolResponse::from)
                .orElse(null);
    }

    public ActiveMatchProposalResponse activeProposal(long memberId) {
        requireMember(memberId);
        MatchProposal proposal = proposals.findActiveForMember(memberId, OffsetDateTime.now(clock)).orElse(null);
        if (proposal == null) {
            return null;
        }
        MatchAttempt attempt = attempts.findById(proposal.getAttemptId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_RESOURCE_NOT_FOUND));
        return ActiveMatchProposalResponse.from(proposal, attempt);
    }

    public MatchingRestrictionResponse restrictions(long memberId) {
        Member member = requireMember(memberId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        MatchCooldown cooldown = cooldowns.findActive(memberId, now).orElse(null);
        return MatchingRestrictionResponse.of(member.getPenaltyScore(), cooldown, now);
    }

    private Member requireMember(long memberId) {
        return members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_RESOURCE_NOT_FOUND));
    }
}
