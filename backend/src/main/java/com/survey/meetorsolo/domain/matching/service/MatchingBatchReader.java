package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.group.MatchingCandidate;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.member.entity.MemberTravelStyle;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import com.survey.meetorsolo.domain.member.repository.MemberTravelStyleRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingBatchReader {
    private final MatchPoolRepository poolRepository;
    private final MemberTravelStyleRepository styleRepository;
    private final JdbcTemplate jdbcTemplate;
    public MatchingBatchReader(MatchPoolRepository poolRepository, MemberTravelStyleRepository styleRepository,
                               JdbcTemplate jdbcTemplate) {
        this.poolRepository = poolRepository; this.styleRepository = styleRepository; this.jdbcTemplate = jdbcTemplate;
    }
    @Transactional(readOnly = true)
    public MatchingBatch read(String lockToken) {
        List<MatchPool> pools = poolRepository.findAllByLockTokenOrderByEnteredAtAscIdAsc(lockToken);
        List<Long> memberIds = pools.stream().map(MatchPool::getMemberId).toList();
        Map<Long, List<TravelStyleCode>> styles = new HashMap<>();
        if (!memberIds.isEmpty()) {
            for (MemberTravelStyle style : styleRepository.findAllByMemberIds(memberIds)) {
                styles.computeIfAbsent(style.getMemberId(), ignored -> new ArrayList<>()).add(style.getStyleCode());
            }
        }
        List<MatchingCandidate> candidates = pools.stream().map(pool -> new MatchingCandidate(
                pool.getId(), pool.getMemberId(), pool.getFestivalId(), pool.getPreferredGroupSize(),
                pool.getAllowMinimumTwo(), pool.getEnteredAt(), styles.getOrDefault(pool.getMemberId(), List.of())
        )).toList();
        return new MatchingBatch(candidates, readBlockedPairs(memberIds));
    }
    private Set<MemberPair> readBlockedPairs(List<Long> memberIds) {
        if (memberIds.size() < 2) return Set.of();
        String placeholders = String.join(",", memberIds.stream().map(ignored -> "?").toList());
        List<Object> args = new ArrayList<>(); args.addAll(memberIds); args.addAll(memberIds);
        Set<MemberPair> pairs = new HashSet<>();
        jdbcTemplate.query("SELECT blocker_member_id, blocked_member_id FROM user_blocks "
                        + "WHERE blocker_member_id IN (" + placeholders + ") AND blocked_member_id IN (" + placeholders + ")",
                (RowCallbackHandler) rs -> pairs.add(MemberPair.of(rs.getLong(1), rs.getLong(2))), args.toArray());
        return Set.copyOf(pairs);
    }
    public record MatchingBatch(List<MatchingCandidate> candidates, Set<MemberPair> blockedPairs) {
        public MatchingBatch { candidates = List.copyOf(candidates); blockedPairs = Set.copyOf(blockedPairs); }
    }
    public record MemberPair(long lowerMemberId, long higherMemberId) {
        public static MemberPair of(long left, long right) { return new MemberPair(Math.min(left, right), Math.max(left, right)); }
    }
}
