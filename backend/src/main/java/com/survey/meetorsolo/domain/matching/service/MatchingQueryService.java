package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.dto.ActiveMatchProposalResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchingRestrictionResponse;
import com.survey.meetorsolo.domain.matching.entity.MatchAttempt;
import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import com.survey.meetorsolo.domain.matching.entity.MatchAttemptMember;
import com.survey.meetorsolo.domain.matching.repository.MatchAttemptRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchCooldownRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchProposalRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchAttemptMemberRepository;
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
    private final MatchGroupRepository groups;
    private final MatchCompletionLockPolicy completionLocks;
    private final MemberRepository members;
    private final MatchAttemptMemberRepository attemptMembers;

    public MatchingQueryService(
            Clock clock,
            MatchPoolRepository pools,
            MatchProposalRepository proposals,
            MatchAttemptRepository attempts,
            MatchCooldownRepository cooldowns,
            MatchGroupRepository groups,
            MatchCompletionLockPolicy completionLocks,
            MemberRepository members,
            MatchAttemptMemberRepository attemptMembers
    ) {
        this.clock = clock;
        this.pools = pools;
        this.proposals = proposals;
        this.attempts = attempts;
        this.cooldowns = cooldowns;
        this.groups = groups;
        this.completionLocks = completionLocks;
        this.members = members;
        this.attemptMembers = attemptMembers;
    }

    public MatchPoolResponse currentPool(long memberId) {
        requireMember(memberId);
        return pools.findFirstByMemberIdOrderByIdDesc(memberId)
                .map(pool -> MatchPoolResponse.from(pool, terminationReason(pool.getId())))
                .orElse(null);
    }

    private String terminationReason(long poolId) {
        MatchAttemptMember member = attemptMembers.findFirstByPoolIdOrderByIdDesc(poolId).orElse(null);
        if (member == null) return null;
        return switch (member.getStatus()) {
            case MatchAttemptMember.STATUS_REJECTED -> "SELF_REJECTED";
            case MatchAttemptMember.STATUS_TIMEOUT -> "SELF_TIMEOUT";
            case MatchAttemptMember.STATUS_EXCLUDED -> "NON_FAULT_TERMINATED";
            default -> attempts.findById(member.getAttemptId())
                    .filter(attempt -> MatchAttempt.STATUS_FAILED.equals(attempt.getStatus()))
                    .map(attempt -> MatchAttemptMember.STATUS_ACCEPTED.equals(member.getStatus())
                            ? "NON_FAULT_TERMINATED" : "SYSTEM_TERMINATED")
                    .orElse(null);
        };
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
        var completedGroup = groups.findLatestCompletedByMemberId(memberId).orElse(null);
        return MatchingRestrictionResponse.of(
                member.getPenaltyScore(), cooldown, completionLocks.evaluate(completedGroup, now), now);
    }

    private Member requireMember(long memberId) {
        return members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_RESOURCE_NOT_FOUND));
    }
}
