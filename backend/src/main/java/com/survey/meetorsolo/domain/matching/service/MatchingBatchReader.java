package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.group.MatchingCandidate;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.member.entity.MemberPreferenceEmbedding;
import com.survey.meetorsolo.domain.member.entity.MemberTravelStyle;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import com.survey.meetorsolo.domain.member.repository.MemberPreferenceEmbeddingRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingBatchReader {
    private final MatchPoolRepository poolRepository;
    private final MemberTravelStyleRepository styleRepository;
    private final MemberPreferenceEmbeddingRepository embeddingRepository;
    private final JdbcTemplate jdbcTemplate;
    public MatchingBatchReader(MatchPoolRepository poolRepository, MemberTravelStyleRepository styleRepository,
                               MemberPreferenceEmbeddingRepository embeddingRepository,
                               JdbcTemplate jdbcTemplate) {
        this.poolRepository = poolRepository;
        this.styleRepository = styleRepository;
        this.embeddingRepository = embeddingRepository;
        this.jdbcTemplate = jdbcTemplate;
    }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public MatchingBatch read(String lockToken) {
        List<MatchPool> pools = poolRepository.findAllByLockTokenOrderByEnteredAtAscIdAsc(lockToken);
        List<Long> memberIds = pools.stream().map(MatchPool::getMemberId).toList();
        Map<Long, List<TravelStyleCode>> styles = new HashMap<>();
        if (!memberIds.isEmpty()) {
            for (MemberTravelStyle style : styleRepository.findAllByMemberIds(memberIds)) {
                styles.computeIfAbsent(style.getMemberId(), ignored -> new ArrayList<>()).add(style.getStyleCode());
            }
        }
        Map<Long, float[]> embeddings = new HashMap<>();
        if (!memberIds.isEmpty()) {
            for (MemberPreferenceEmbedding emb : embeddingRepository.findAllByMemberIdInAndEmbeddingStatus(
                    memberIds, MemberPreferenceEmbedding.STATUS_COMPLETED)) {
                embeddings.put(emb.getMember().getId(), emb.getEmbedding());
            }
        }
        List<MatchingCandidate> candidates = pools.stream().map(pool -> new MatchingCandidate(
                pool.getId(), pool.getMemberId(), pool.getCheckinId(), pool.getFestivalId(), pool.getPreferredGroupSize(),
                pool.getAllowMinimumTwo(), pool.getEnteredAt(), styles.getOrDefault(pool.getMemberId(), List.of()),
                embeddings.get(pool.getMemberId())
        )).toList();
        return new MatchingBatch(candidates, readBlockedPairs(memberIds), readExcludedPairs(candidates));
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
    private Set<MatchOpponentPair> readExcludedPairs(List<MatchingCandidate> candidates) {
        if (candidates.size() < 2) return Set.of();
        List<Long> checkinIds = candidates.stream().map(MatchingCandidate::checkinId).toList();
        String placeholders = String.join(",", checkinIds.stream().map(ignored -> "?").toList());
        List<Object> args = new ArrayList<>(); args.addAll(checkinIds); args.addAll(checkinIds);
        Set<MatchOpponentPair> pairs = new HashSet<>();
        jdbcTemplate.query("SELECT lower_member_id,higher_member_id,lower_checkin_id,higher_checkin_id "
                        + "FROM match_opponent_exclusions WHERE lower_checkin_id IN (" + placeholders
                        + ") AND higher_checkin_id IN (" + placeholders + ")",
                (RowCallbackHandler) rs -> pairs.add(new MatchOpponentPair(
                        rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4))), args.toArray());
        return Set.copyOf(pairs);
    }
    public record MatchingBatch(List<MatchingCandidate> candidates, Set<MemberPair> blockedPairs,
                                Set<MatchOpponentPair> excludedPairs) {
        public MatchingBatch {
            candidates = List.copyOf(candidates);
            blockedPairs = Set.copyOf(blockedPairs);
            excludedPairs = Set.copyOf(excludedPairs);
        }
    }
    public record MemberPair(long lowerMemberId, long higherMemberId) {
        public static MemberPair of(long left, long right) { return new MemberPair(Math.min(left, right), Math.max(left, right)); }
    }
}
