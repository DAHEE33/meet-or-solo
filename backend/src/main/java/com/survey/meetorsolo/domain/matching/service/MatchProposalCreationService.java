package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchAttempt;
import com.survey.meetorsolo.domain.matching.entity.MatchAttemptMember;
import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchingCandidate;
import com.survey.meetorsolo.domain.matching.repository.MatchAttemptMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchAttemptRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchProposalRepository;
import com.survey.meetorsolo.domain.matching.scoring.TravelStyleScorer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchProposalCreationService {
    private final MatchPoolRepository poolRepository;
    private final MatchAttemptRepository attemptRepository;
    private final MatchAttemptMemberRepository memberRepository;
    private final MatchProposalRepository proposalRepository;
    private final TravelStyleScorer scorer;
    private final JdbcTemplate jdbcTemplate;

    public MatchProposalCreationService(MatchPoolRepository poolRepository, MatchAttemptRepository attemptRepository,
            MatchAttemptMemberRepository memberRepository, MatchProposalRepository proposalRepository,
            TravelStyleScorer scorer, JdbcTemplate jdbcTemplate) {
        this.poolRepository = poolRepository; this.attemptRepository = attemptRepository;
        this.memberRepository = memberRepository; this.proposalRepository = proposalRepository;
        this.scorer = scorer; this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MatchProposalCreationResult createInitial(MatchGroupCombination group, String lockToken,
                                                      OffsetDateTime now, Duration proposalTimeout) {
        Objects.requireNonNull(group, "group은 필수입니다.");
        Objects.requireNonNull(now, "now는 필수입니다.");
        if (lockToken == null || lockToken.isBlank()) throw new IllegalArgumentException("lockToken은 필수입니다.");
        if (proposalTimeout == null || proposalTimeout.isZero() || proposalTimeout.isNegative()) {
            throw new IllegalArgumentException("proposalTimeout은 양수여야 합니다.");
        }
        List<MatchingCandidate> candidates = group.candidates();
        validateCandidateShape(candidates);
        List<Long> poolIds = candidates.stream().map(MatchingCandidate::poolId).sorted().toList();
        List<MatchPool> pools = poolRepository.findAllByIdsForUpdate(poolIds);
        validateLockedPools(pools, candidates, lockToken, now);

        OffsetDateTime expiresAt = now.plus(proposalTimeout);
        MatchAttempt attempt = attemptRepository.saveAndFlush(MatchAttempt.initial(
                candidates.get(0).festivalId(), candidates.size(), group.score(), now, expiresAt));
        for (MatchingCandidate candidate : candidates) {
            BigDecimal memberScore = memberScore(candidate, candidates);
            memberRepository.save(MatchAttemptMember.proposed(
                    attempt.getId(), candidate.memberId(), candidate.poolId(), memberScore, now));
            proposalRepository.save(MatchProposal.initial(attempt.getId(), candidate.memberId(), now, expiresAt));
        }
        pools.forEach(pool -> pool.propose(now));
        memberRepository.flush();
        proposalRepository.flush();
        poolRepository.flush();
        return new MatchProposalCreationResult(attempt.getId(), poolIds);
    }

    private void validateCandidateShape(List<MatchingCandidate> candidates) {
        if (candidates.size() < 2 || candidates.size() > 4) fail("그룹 인원은 2~4명이어야 합니다.");
        int preferredSize = candidates.get(0).preferredGroupSize();
        long festivalId = candidates.get(0).festivalId();
        if (candidates.size() != preferredSize) fail("정확한 희망 인원으로만 proposal을 생성할 수 있습니다.");
        if (candidates.stream().anyMatch(c -> c.preferredGroupSize() != preferredSize)) fail("희망 인원이 서로 다릅니다.");
        if (candidates.stream().anyMatch(c -> c.festivalId() != festivalId)) fail("축제가 서로 다릅니다.");
        if (new HashSet<>(candidates.stream().map(MatchingCandidate::poolId).toList()).size() != candidates.size())
            fail("poolId가 중복되었습니다.");
        if (new HashSet<>(candidates.stream().map(MatchingCandidate::memberId).toList()).size() != candidates.size())
            fail("memberId가 중복되었습니다.");
    }

    private void validateLockedPools(List<MatchPool> pools, List<MatchingCandidate> candidates,
                                     String lockToken, OffsetDateTime now) {
        if (pools.size() != candidates.size()) fail("요청한 pool 수와 잠금 조회 수가 다릅니다.");
        Map<Long, MatchingCandidate> byPool = candidates.stream()
                .collect(Collectors.toMap(MatchingCandidate::poolId, Function.identity()));
        for (MatchPool pool : pools) {
            MatchingCandidate candidate = byPool.get(pool.getId());
            if (candidate == null || pool.getMemberId() != candidate.memberId()
                    || pool.getFestivalId() != candidate.festivalId()
                    || pool.getPreferredGroupSize() != candidate.preferredGroupSize()) fail("pool snapshot이 변경되었습니다.");
            if (!MatchPool.STATUS_LOCKED.equals(pool.getStatus())) fail("LOCKED 상태가 아닌 pool이 포함되었습니다.");
            if (!lockToken.equals(pool.getLockToken())) fail("lock_token 소유권이 일치하지 않습니다.");
            if (pool.getLockedAt() == null) fail("locked_at이 없는 LOCKED pool입니다.");
            if (!pool.getSearchExpiresAt().isAfter(now)) fail("검색 시간이 만료된 pool입니다.");
        }
        List<Long> poolIds = pools.stream().map(MatchPool::getId).toList();
        List<Long> memberIds = pools.stream().map(MatchPool::getMemberId).toList();
        if (countValidCheckins(poolIds, now) != pools.size()) fail("유효하지 않은 check-in이 포함되었습니다.");
        if (countActiveCooldowns(memberIds, now) > 0) fail("active cooldown 회원이 포함되었습니다.");
        if (countBlocks(memberIds) > 0) fail("차단 관계 회원이 포함되었습니다.");
    }

    private int countValidCheckins(List<Long> poolIds, OffsetDateTime now) {
        return queryCount("SELECT count(*) FROM match_pools p JOIN festival_checkins c ON c.id=p.checkin_id "
                + "WHERE p.id IN (" + placeholders(poolIds.size()) + ") AND c.member_id=p.member_id "
                + "AND c.festival_id=p.festival_id AND c.status='ACTIVE' AND c.expires_at>?", poolIds, now);
    }
    private int countActiveCooldowns(List<Long> memberIds, OffsetDateTime now) {
        List<Object> args = new ArrayList<>(memberIds); args.add(now); args.add(now);
        return queryCount("SELECT count(*) FROM match_cooldowns WHERE member_id IN (" + placeholders(memberIds.size())
                + ") AND status='ACTIVE' AND starts_at<=? AND expires_at>?", args);
    }
    private int countBlocks(List<Long> memberIds) {
        List<Object> args = new ArrayList<>(memberIds); args.addAll(memberIds);
        return queryCount("SELECT count(*) FROM user_blocks WHERE blocker_member_id IN (" + placeholders(memberIds.size())
                + ") AND blocked_member_id IN (" + placeholders(memberIds.size()) + ")", args);
    }
    private int queryCount(String sql, List<?> values, Object... tail) {
        List<Object> args = new ArrayList<>(values); args.addAll(List.of(tail));
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args.toArray());
        return count == null ? 0 : count;
    }
    private String placeholders(int size) { return String.join(",", java.util.Collections.nCopies(size, "?")); }
    private BigDecimal memberScore(MatchingCandidate target, List<MatchingCandidate> candidates) {
        BigDecimal total = BigDecimal.ZERO;
        int pairs = 0;
        for (MatchingCandidate other : candidates) if (other.poolId() != target.poolId()) {
            total = total.add(scorer.score(target.travelStyles(), other.travelStyles())); pairs++;
        }
        return total.divide(BigDecimal.valueOf(pairs), TravelStyleScorer.SCORE_SCALE, RoundingMode.HALF_UP);
    }
    private void fail(String message) { throw new MatchProposalCreationException(message); }
}
