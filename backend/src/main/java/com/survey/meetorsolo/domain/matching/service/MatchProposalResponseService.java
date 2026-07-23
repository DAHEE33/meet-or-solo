package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.*;
import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.repository.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchProposalResponseService {
    private final MatchAttemptRepository attempts;
    private final MatchProposalRepository proposals;
    private final MatchAttemptMemberRepository members;
    private final MatchResponseRepository responses;
    private final MatchPoolRepository pools;
    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchPenaltyCooldownService penaltyCooldowns;
    private final MatchingPenaltyPolicy penaltyPolicy;
    private final Duration proposalTimeout;

    public MatchProposalResponseService(MatchAttemptRepository attempts, MatchProposalRepository proposals,
            MatchAttemptMemberRepository members, MatchResponseRepository responses, MatchPoolRepository pools,
            MatchGroupRepository groups, MatchGroupMemberRepository groupMembers,
            MatchPenaltyCooldownService penaltyCooldowns, MatchingPenaltyPolicy penaltyPolicy,
            MatchingSchedulerProperties properties) {
        this.attempts=attempts; this.proposals=proposals; this.members=members; this.responses=responses;
        this.pools=pools; this.groups=groups; this.groupMembers=groupMembers;
        this.penaltyCooldowns=penaltyCooldowns; this.penaltyPolicy=penaltyPolicy;
        this.proposalTimeout=properties.proposalTimeout();
    }

    @Transactional
    public MatchProposalResponseResult respond(long proposalId, long memberId, String requestedResponse,
                                                OffsetDateTime now) {
        MatchProposal snapshot = proposals.findById(proposalId).orElseThrow(() -> failure("proposal이 없습니다."));
        validateRequestedResponse(snapshot, requestedResponse);
        return process(snapshot.getAttemptId(), proposalId, memberId, requestedResponse, now, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MatchProposalResponseResult timeoutAttempt(long attemptId, OffsetDateTime now) {
        MatchAttempt attempt = lockAttempt(attemptId);
        if (!isActive(attempt)) return null;
        MatchProposal candidate = proposals
                .findFirstByAttemptIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        attemptId, MatchProposal.STATUS_SENT, now).orElse(null);
        if (candidate == null) return null;
        return processLockedAttempt(attempt, candidate.getId(), candidate.getMemberId(),
                MatchProposal.STATUS_TIMEOUT, now, true);
    }

    private MatchProposalResponseResult process(long attemptId, long proposalId, long memberId,
            String requestedResponse, OffsetDateTime now, boolean timeout) {
        if (now == null) throw new IllegalArgumentException("now는 필수입니다.");
        MatchAttempt attempt = lockAttempt(attemptId);
        return processLockedAttempt(attempt, proposalId, memberId, requestedResponse, now, timeout);
    }

    private MatchProposalResponseResult processLockedAttempt(MatchAttempt attempt, long proposalId, long memberId,
            String requestedResponse, OffsetDateTime now, boolean schedulerTimeout) {
        MatchProposal proposal = proposals.findByIdForUpdate(proposalId).orElseThrow(() -> failure("proposal이 없습니다."));
        validateProposalOwnership(attempt, proposal, memberId);
        MatchAttemptMember member = members.findForUpdate(attempt.getId(), memberId)
                .orElseThrow(() -> failure("attempt member가 없습니다."));
        MatchResponse existing = responses.findByProposalIdAndMemberId(proposalId, memberId).orElse(null);
        if (existing != null) return existingResult(attempt, proposal, existing, requestedResponse, schedulerTimeout);
        if (!isExpectedAttemptStatus(attempt, proposal)) throw failure("이미 종료됐거나 회차가 다른 attempt입니다.");
        if (!MatchProposal.STATUS_SENT.equals(proposal.getStatus()) || !isExpectedMemberStatus(member, proposal)) {
            throw failure("이미 처리된 proposal입니다.");
        }

        String effective = schedulerTimeout || !now.isBefore(proposal.getExpiresAt())
                ? MatchProposal.STATUS_TIMEOUT : requestedResponse;
        proposal.respond(proposalStatus(effective), now);
        if (isInitial(proposal)) member.respond(effective, now);
        responses.save(MatchResponse.of(proposalId, attempt.getId(), memberId, effective, now));
        applyImmediatePenalty(proposal, effective, now);

        if (isInitial(proposal)) completeInitialRoundIfReady(attempt, now);
        else completeInsufficientRound(attempt, member, effective, now);
        flushAll();
        return new MatchProposalResponseResult(attempt.getId(), proposalId, effective, attempt.getStatus());
    }

    private void validateProposalOwnership(MatchAttempt attempt, MatchProposal proposal, long memberId) {
        if (!proposal.getAttemptId().equals(attempt.getId()) || !proposal.getMemberId().equals(memberId))
            throw failure("proposal, attempt, member 소유 관계가 올바르지 않습니다.");
        boolean initial = isInitial(proposal) && proposal.getProposalRound() == 1;
        boolean insufficient = isInsufficient(proposal) && proposal.getProposalRound() == 2;
        if (!initial && !insufficient) throw failure("지원하지 않는 proposal 회차입니다.");
    }

    private MatchProposalResponseResult existingResult(MatchAttempt attempt, MatchProposal proposal,
            MatchResponse existing, String requested, boolean schedulerTimeout) {
        if (!existing.getProposalId().equals(proposal.getId()) || !existing.getAttemptId().equals(attempt.getId())
                || !existing.getMemberId().equals(proposal.getMemberId())) throw failure("기존 response 소유 관계가 올바르지 않습니다.");
        String expected = schedulerTimeout ? MatchProposal.STATUS_TIMEOUT : requested;
        if (!existing.getResponse().equals(expected)) throw failure("기존 응답을 변경할 수 없습니다.");
        return new MatchProposalResponseResult(attempt.getId(), proposal.getId(), existing.getResponse(), attempt.getStatus());
    }

    private void completeInitialRoundIfReady(MatchAttempt attempt, OffsetDateTime now) {
        List<MatchAttemptMember> attemptMembers = members.findAllByAttemptIdOrderByIdAsc(attempt.getId());
        if (attemptMembers.stream().anyMatch(value -> MatchAttemptMember.STATUS_PROPOSED.equals(value.getStatus()))) return;
        applyRoundOneRejectionCooldowns(attempt.getId(), now);
        List<MatchAttemptMember> accepted = attemptMembers.stream()
                .filter(value -> MatchAttemptMember.STATUS_ACCEPTED.equals(value.getStatus())).toList();
        if (accepted.size() == attempt.getTargetGroupSize()) {
            confirmAttempt(attempt, accepted, now);
            return;
        }
        Map<Long, MatchPool> lockedPools = lockAttemptPools(attemptMembers);
        if (canStartInsufficientRound(attempt, accepted, lockedPools)) {
            startInsufficientRound(attempt, accepted, attemptMembers, lockedPools, now);
            return;
        }
        finishFailedAttempt(attempt, attemptMembers, lockedPools, "INITIAL_MATCH_INSUFFICIENT", now);
    }

    private void applyImmediatePenalty(MatchProposal proposal, String response, OffsetDateTime now) {
        if (isInitial(proposal) && MatchProposal.STATUS_TIMEOUT.equals(response)) {
            penaltyCooldowns.apply(proposal, penaltyPolicy.roundOneTimeout(), now);
            return;
        }
        if (!isInsufficient(proposal)) {
            return;
        }
        if ("CANCEL_CURRENT_MEMBERS".equals(response)) {
            penaltyCooldowns.apply(proposal, penaltyPolicy.roundTwoCancelled(), now);
        } else if (MatchProposal.STATUS_TIMEOUT.equals(response)) {
            penaltyCooldowns.apply(proposal, penaltyPolicy.roundTwoTimeout(), now);
        }
    }

    private void applyRoundOneRejectionCooldowns(long attemptId, OffsetDateTime now) {
        proposals.findAllByAttemptIdOrderByIdAsc(attemptId).stream()
                .filter(this::isInitial)
                .filter(proposal -> MatchProposal.STATUS_REJECTED.equals(proposal.getStatus()))
                .forEach(proposal -> penaltyCooldowns.apply(
                        proposal, penaltyPolicy.roundOneRejected(), now));
    }

    private boolean canStartInsufficientRound(MatchAttempt attempt, List<MatchAttemptMember> accepted,
            Map<Long, MatchPool> lockedPools) {
        if (attempt.getTargetGroupSize() < 3 || accepted.size() < 2
                || accepted.size() >= attempt.getTargetGroupSize()
                || proposals.existsByAttemptIdAndProposalRound(attempt.getId(), 2)) return false;
        return accepted.stream().map(value -> lockedPools.get(value.getPoolId()))
                .allMatch(pool -> pool != null && Boolean.TRUE.equals(pool.getAllowMinimumTwo()));
    }

    private void startInsufficientRound(MatchAttempt attempt, List<MatchAttemptMember> accepted,
            List<MatchAttemptMember> attemptMembers, Map<Long, MatchPool> lockedPools, OffsetDateTime now) {
        settleInitialNonAcceptedPools(attemptMembers, lockedPools, now);
        OffsetDateTime expiresAt = now.plus(proposalTimeout);
        for (MatchAttemptMember value : accepted) {
            proposals.save(MatchProposal.insufficientMembers(
                    attempt.getId(), value.getMemberId(), now, expiresAt));
        }
        attempt.enterInsufficientMembers(now, expiresAt);
    }

    private void completeInsufficientRound(MatchAttempt attempt, MatchAttemptMember responsible,
            String response, OffsetDateTime now) {
        if ("CANCEL_CURRENT_MEMBERS".equals(response) || MatchProposal.STATUS_TIMEOUT.equals(response)) {
            failInsufficientRound(attempt, responsible, response, now);
            return;
        }
        List<MatchProposal> roundTwo = proposals.findAllByAttemptIdOrderByIdAsc(attempt.getId()).stream()
                .filter(value -> value.getProposalRound() == 2).toList();
        if (roundTwo.stream().allMatch(value -> MatchProposal.STATUS_ACCEPTED.equals(value.getStatus()))) {
            List<MatchAttemptMember> accepted = members.findAllByAttemptIdOrderByIdAsc(attempt.getId()).stream()
                    .filter(value -> MatchAttemptMember.STATUS_ACCEPTED.equals(value.getStatus())).toList();
            confirmAttempt(attempt, accepted, now);
        }
    }

    private void failInsufficientRound(MatchAttempt attempt, MatchAttemptMember responsible,
            String reason, OffsetDateTime now) {
        List<MatchProposal> attemptProposals = proposals.findAllByAttemptIdOrderByIdAsc(attempt.getId());
        List<MatchAttemptMember> attemptMembers = members.findAllByAttemptIdOrderByIdAsc(attempt.getId());
        attemptProposals.stream().filter(value -> value.getProposalRound() == 2).forEach(value -> value.expire(now));
        Map<Long, MatchAttemptMember> byPool = attemptMembers.stream().collect(Collectors.toMap(MatchAttemptMember::getPoolId, Function.identity()));
        List<MatchPool> lockedPools = pools.findResponsePoolsForUpdate(byPool.keySet().stream().sorted().toList());
        if (lockedPools.size() != byPool.size()) throw failure("attempt pool을 모두 잠글 수 없습니다.");
        for (MatchPool pool : lockedPools) {
            MatchAttemptMember value = byPool.get(pool.getId());
            if (value.getId().equals(responsible.getId())) pool.cancel(now);
            else if (MatchAttemptMember.STATUS_ACCEPTED.equals(value.getStatus())) pool.releaseAfterFailedAttempt(now);
        }
        attempt.fail(reason, now);
    }

    private void finishFailedAttempt(MatchAttempt attempt, List<MatchAttemptMember> attemptMembers,
            Map<Long, MatchPool> lockedPools, String reason, OffsetDateTime now) {
        settleInitialNonAcceptedPools(attemptMembers, lockedPools, now);
        attemptMembers.stream().filter(value -> MatchAttemptMember.STATUS_ACCEPTED.equals(value.getStatus()))
                .map(value -> lockedPools.get(value.getPoolId())).forEach(pool -> pool.releaseAfterFailedAttempt(now));
        attempt.fail(reason, now);
    }

    private void settleInitialNonAcceptedPools(List<MatchAttemptMember> attemptMembers,
            Map<Long, MatchPool> lockedPools, OffsetDateTime now) {
        attemptMembers.stream().filter(value -> !MatchAttemptMember.STATUS_ACCEPTED.equals(value.getStatus()))
                .map(value -> lockedPools.get(value.getPoolId())).forEach(pool -> pool.cancel(now));
    }

    private Map<Long, MatchPool> lockAttemptPools(List<MatchAttemptMember> attemptMembers) {
        List<Long> poolIds = attemptMembers.stream().map(MatchAttemptMember::getPoolId).sorted().toList();
        List<MatchPool> lockedPools = pools.findResponsePoolsForUpdate(poolIds);
        if (lockedPools.size() != poolIds.size()) throw failure("attempt pool을 모두 잠글 수 없습니다.");
        return lockedPools.stream().collect(Collectors.toMap(MatchPool::getId, Function.identity()));
    }

    private void confirmAttempt(MatchAttempt attempt, List<MatchAttemptMember> accepted, OffsetDateTime now) {
        if (accepted.size() < 2) return;
        List<MatchPool> lockedPools = pools.findResponsePoolsForUpdate(
                accepted.stream().map(MatchAttemptMember::getPoolId).sorted().toList());
        if (lockedPools.size() != accepted.size()) throw failure("attempt pool을 모두 잠글 수 없습니다.");
        MatchGroup group = groups.saveAndFlush(MatchGroup.confirmed(
                attempt.getId(), attempt.getFestivalId(), accepted.size(), now));
        for (MatchAttemptMember value : accepted) groupMembers.save(MatchGroupMember.joined(group.getId(), value.getMemberId(), now));
        lockedPools.forEach(pool -> pool.match(now));
        attempt.confirm(now);
    }

    private void validateRequestedResponse(MatchProposal proposal, String response) {
        if (isInitial(proposal)) {
            if (!MatchProposal.STATUS_ACCEPTED.equals(response) && !MatchProposal.STATUS_REJECTED.equals(response))
                throw new IllegalArgumentException("최초 제안 응답은 ACCEPTED 또는 REJECTED여야 합니다.");
        } else if (!"START_WITH_CURRENT_MEMBERS".equals(response) && !"CANCEL_CURRENT_MEMBERS".equals(response)) {
            throw new IllegalArgumentException("인원 미달 응답은 START_WITH_CURRENT_MEMBERS 또는 CANCEL_CURRENT_MEMBERS여야 합니다.");
        }
    }
    private String proposalStatus(String response) {
        if ("START_WITH_CURRENT_MEMBERS".equals(response)) return MatchProposal.STATUS_ACCEPTED;
        if ("CANCEL_CURRENT_MEMBERS".equals(response)) return MatchProposal.STATUS_REJECTED;
        return response;
    }
    private boolean isExpectedAttemptStatus(MatchAttempt attempt, MatchProposal proposal) {
        return isInitial(proposal) ? MatchAttempt.STATUS_WAITING_RESPONSES.equals(attempt.getStatus())
                : MatchAttempt.STATUS_INSUFFICIENT_MEMBERS.equals(attempt.getStatus());
    }
    private boolean isExpectedMemberStatus(MatchAttemptMember member, MatchProposal proposal) {
        return isInitial(proposal) ? MatchAttemptMember.STATUS_PROPOSED.equals(member.getStatus())
                : MatchAttemptMember.STATUS_ACCEPTED.equals(member.getStatus());
    }
    private boolean isActive(MatchAttempt attempt) {
        return MatchAttempt.STATUS_WAITING_RESPONSES.equals(attempt.getStatus())
                || MatchAttempt.STATUS_INSUFFICIENT_MEMBERS.equals(attempt.getStatus());
    }
    private boolean isInitial(MatchProposal proposal) { return MatchProposal.TYPE_INITIAL_MATCH.equals(proposal.getProposalType()); }
    private boolean isInsufficient(MatchProposal proposal) { return MatchProposal.TYPE_INSUFFICIENT_MEMBERS_CONFIRMATION.equals(proposal.getProposalType()); }
    private MatchAttempt lockAttempt(long id) { return attempts.findByIdForUpdate(id).orElseThrow(() -> failure("attempt가 없습니다.")); }
    private void flushAll() { responses.flush(); proposals.flush(); members.flush(); groupMembers.flush(); pools.flush(); attempts.flush(); }
    private MatchProposalResponseException failure(String message) { return new MatchProposalResponseException(message); }
}
