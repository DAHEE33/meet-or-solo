package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.*;
import com.survey.meetorsolo.domain.matching.repository.*;
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

    public MatchProposalResponseService(MatchAttemptRepository attempts, MatchProposalRepository proposals,
            MatchAttemptMemberRepository members, MatchResponseRepository responses, MatchPoolRepository pools,
            MatchGroupRepository groups, MatchGroupMemberRepository groupMembers) {
        this.attempts=attempts; this.proposals=proposals; this.members=members; this.responses=responses;
        this.pools=pools; this.groups=groups; this.groupMembers=groupMembers;
    }

    @Transactional
    public MatchProposalResponseResult respond(long proposalId, long memberId, String requestedResponse,
                                                OffsetDateTime now) {
        if (!MatchProposal.STATUS_ACCEPTED.equals(requestedResponse)
                && !MatchProposal.STATUS_REJECTED.equals(requestedResponse)) {
            throw new IllegalArgumentException("사용자 응답은 ACCEPTED 또는 REJECTED여야 합니다.");
        }
        MatchProposal snapshot = proposals.findById(proposalId).orElseThrow(() -> failure("proposal이 없습니다."));
        return process(snapshot.getAttemptId(), proposalId, memberId, requestedResponse, now, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MatchProposalResponseResult timeoutAttempt(long attemptId, OffsetDateTime now) {
        MatchAttempt attempt = lockAttempt(attemptId);
        if (!MatchAttempt.STATUS_WAITING_RESPONSES.equals(attempt.getStatus())) return null;
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
        if (!MatchAttempt.STATUS_WAITING_RESPONSES.equals(attempt.getStatus())) throw failure("이미 종료된 attempt입니다.");
        if (!MatchProposal.STATUS_SENT.equals(proposal.getStatus()) || !MatchAttemptMember.STATUS_PROPOSED.equals(member.getStatus())) {
            throw failure("이미 처리된 proposal입니다.");
        }

        String effective = schedulerTimeout || !now.isBefore(proposal.getExpiresAt())
                ? MatchProposal.STATUS_TIMEOUT : requestedResponse;
        proposal.respond(effective, now);
        member.respond(effective, now);
        responses.save(MatchResponse.of(proposalId, attempt.getId(), memberId, effective, now));

        if (MatchProposal.STATUS_REJECTED.equals(effective) || MatchProposal.STATUS_TIMEOUT.equals(effective)) {
            failAttempt(attempt, proposal, member, effective, now);
        } else if (allAccepted(attempt.getId())) {
            confirmAttempt(attempt, now);
        }
        flushAll();
        return new MatchProposalResponseResult(attempt.getId(), proposalId, effective, attempt.getStatus());
    }

    private void validateProposalOwnership(MatchAttempt attempt, MatchProposal proposal, long memberId) {
        if (!proposal.getAttemptId().equals(attempt.getId()) || !proposal.getMemberId().equals(memberId))
            throw failure("proposal, attempt, member 소유 관계가 올바르지 않습니다.");
        if (!MatchProposal.TYPE_INITIAL_MATCH.equals(proposal.getProposalType()) || proposal.getProposalRound() != 1)
            throw failure("INITIAL_MATCH round 1 proposal만 처리할 수 있습니다.");
    }

    private MatchProposalResponseResult existingResult(MatchAttempt attempt, MatchProposal proposal,
            MatchResponse existing, String requested, boolean schedulerTimeout) {
        if (!existing.getProposalId().equals(proposal.getId()) || !existing.getAttemptId().equals(attempt.getId())
                || !existing.getMemberId().equals(proposal.getMemberId())) throw failure("기존 response 소유 관계가 올바르지 않습니다.");
        String expected = schedulerTimeout ? MatchProposal.STATUS_TIMEOUT : requested;
        if (!existing.getResponse().equals(expected)) throw failure("기존 응답을 변경할 수 없습니다.");
        return new MatchProposalResponseResult(attempt.getId(), proposal.getId(), existing.getResponse(), attempt.getStatus());
    }

    private void failAttempt(MatchAttempt attempt, MatchProposal responsibleProposal,
            MatchAttemptMember responsible, String reason, OffsetDateTime now) {
        List<MatchProposal> attemptProposals = proposals.findAllByAttemptIdOrderByIdAsc(attempt.getId());
        List<MatchAttemptMember> attemptMembers = members.findAllByAttemptIdOrderByIdAsc(attempt.getId());
        attemptProposals.forEach(value -> { if (!value.getId().equals(responsibleProposal.getId())) value.expire(now); });
        attemptMembers.forEach(value -> { if (!value.getId().equals(responsible.getId())) value.exclude(now); });
        Map<Long, MatchAttemptMember> byPool = attemptMembers.stream().collect(Collectors.toMap(MatchAttemptMember::getPoolId, Function.identity()));
        List<MatchPool> lockedPools = pools.findResponsePoolsForUpdate(byPool.keySet().stream().sorted().toList());
        if (lockedPools.size() != byPool.size()) throw failure("attempt pool을 모두 잠글 수 없습니다.");
        for (MatchPool pool : lockedPools) {
            MatchAttemptMember value = byPool.get(pool.getId());
            if (value.getId().equals(responsible.getId())) pool.cancel(now); else pool.releaseAfterFailedAttempt(now);
        }
        attempt.fail(reason, now);
    }

    private void confirmAttempt(MatchAttempt attempt, OffsetDateTime now) {
        List<MatchAttemptMember> accepted = members.findAllByAttemptIdOrderByIdAsc(attempt.getId());
        if (accepted.size() != attempt.getTargetGroupSize()
                || accepted.stream().anyMatch(value -> !MatchAttemptMember.STATUS_ACCEPTED.equals(value.getStatus()))) return;
        List<MatchPool> lockedPools = pools.findResponsePoolsForUpdate(
                accepted.stream().map(MatchAttemptMember::getPoolId).sorted().toList());
        if (lockedPools.size() != accepted.size()) throw failure("attempt pool을 모두 잠글 수 없습니다.");
        MatchGroup group = groups.saveAndFlush(MatchGroup.confirmed(
                attempt.getId(), attempt.getFestivalId(), accepted.size(), now));
        for (MatchAttemptMember value : accepted) groupMembers.save(MatchGroupMember.joined(group.getId(), value.getMemberId(), now));
        lockedPools.forEach(pool -> pool.match(now));
        attempt.confirm(now);
    }

    private boolean allAccepted(long attemptId) {
        List<MatchAttemptMember> values = members.findAllByAttemptIdOrderByIdAsc(attemptId);
        return !values.isEmpty() && values.stream().allMatch(value -> MatchAttemptMember.STATUS_ACCEPTED.equals(value.getStatus()));
    }
    private MatchAttempt lockAttempt(long id) { return attempts.findByIdForUpdate(id).orElseThrow(() -> failure("attempt가 없습니다.")); }
    private void flushAll() { responses.flush(); proposals.flush(); members.flush(); groupMembers.flush(); pools.flush(); attempts.flush(); }
    private MatchProposalResponseException failure(String message) { return new MatchProposalResponseException(message); }
}
