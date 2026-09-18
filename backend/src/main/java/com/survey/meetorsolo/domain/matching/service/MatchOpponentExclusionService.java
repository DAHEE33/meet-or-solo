package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchAttemptMember;
import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MatchOpponentExclusionService {

    private final JdbcTemplate jdbcTemplate;

    public MatchOpponentExclusionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MatchOpponentPair> pairs(Collection<MatchPool> pools) {
        List<MatchPool> ordered = pools.stream()
                .sorted(Comparator.comparing(MatchPool::getMemberId).thenComparing(MatchPool::getCheckinId))
                .toList();
        List<MatchOpponentPair> result = new ArrayList<>();
        for (int left = 0; left < ordered.size(); left++) {
            for (int right = left + 1; right < ordered.size(); right++) {
                MatchPool first = ordered.get(left);
                MatchPool second = ordered.get(right);
                result.add(MatchOpponentPair.of(first.getMemberId(), first.getCheckinId(),
                        second.getMemberId(), second.getCheckinId()));
            }
        }
        return result.stream().sorted().toList();
    }

    public void lockPairs(Collection<MatchOpponentPair> pairs) {
        pairs.stream().distinct().sorted().forEach(pair -> {
            MatchOpponentPair.AdvisoryLockKey key = pair.advisoryLockKey();
            jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?, ?)", Object.class,
                    key.first(), key.second());
        });
    }

    public void createForExplicitRejection(long sourceProposalId, long rejectedByMemberId,
                                           List<MatchAttemptMember> attemptMembers,
                                           List<MatchPool> attemptPools,
                                           OffsetDateTime now) {
        Map<Long, MatchPool> poolsById = attemptPools.stream()
                .collect(Collectors.toMap(MatchPool::getId, Function.identity()));
        MatchAttemptMember rejector = attemptMembers.stream()
                .filter(member -> member.getMemberId() == rejectedByMemberId)
                .findFirst()
                .orElseThrow(() -> new MatchProposalResponseException("거절 회원의 attempt member가 없습니다."));
        MatchPool rejectorPool = requireOwnedPool(rejector, poolsById);
        List<MatchOpponentPair> pairs = attemptMembers.stream()
                .filter(member -> member.getMemberId() != rejectedByMemberId)
                .map(member -> {
                    MatchPool otherPool = requireOwnedPool(member, poolsById);
                    return MatchOpponentPair.of(rejectorPool.getMemberId(), rejectorPool.getCheckinId(),
                            otherPool.getMemberId(), otherPool.getCheckinId());
                })
                .distinct()
                .sorted()
                .toList();
        lockPairs(pairs);
        for (MatchOpponentPair pair : pairs) {
            jdbcTemplate.update("""
                    INSERT INTO match_opponent_exclusions(
                        lower_member_id, higher_member_id, lower_checkin_id, higher_checkin_id,
                        rejected_by_member_id, source_proposal_id, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, pair.lowerMemberId(), pair.higherMemberId(), pair.lowerCheckinId(),
                    pair.higherCheckinId(), rejectedByMemberId, sourceProposalId, now);
        }
    }

    public boolean existsAnyLocked(Collection<MatchPool> pools) {
        List<MatchOpponentPair> pairs = pairs(pools);
        lockPairs(pairs);
        for (MatchOpponentPair pair : pairs) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM match_opponent_exclusions
                    WHERE lower_member_id=? AND higher_member_id=?
                      AND lower_checkin_id=? AND higher_checkin_id=?
                    """, Integer.class, pair.lowerMemberId(), pair.higherMemberId(),
                    pair.lowerCheckinId(), pair.higherCheckinId());
            if (count != null && count > 0) return true;
        }
        return false;
    }

    private MatchPool requireOwnedPool(MatchAttemptMember member, Map<Long, MatchPool> poolsById) {
        MatchPool pool = poolsById.get(member.getPoolId());
        if (pool == null || !pool.getMemberId().equals(member.getMemberId())) {
            throw new MatchProposalResponseException("attempt member와 pool 소유 관계가 올바르지 않습니다.");
        }
        Integer valid = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM festival_checkins
                WHERE id=? AND member_id=? AND festival_id=?
                """, Integer.class, pool.getCheckinId(), pool.getMemberId(), pool.getFestivalId());
        if (valid == null || valid != 1) {
            throw new MatchProposalResponseException("pool의 check-in 소유 관계가 올바르지 않습니다.");
        }
        return pool;
    }
}
