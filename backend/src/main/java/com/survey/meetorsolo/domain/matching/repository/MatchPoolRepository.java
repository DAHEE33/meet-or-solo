package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPoolRepository extends JpaRepository<MatchPool, Long> {

    Optional<MatchPool> findFirstByMemberIdOrderByIdDesc(long memberId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM match_pools
                WHERE member_id = :memberId
                  AND status IN ('WAITING', 'LOCKED', 'PROPOSED')
            )
            """, nativeQuery = true)
    boolean existsActiveByMemberId(@Param("memberId") long memberId);

    @Query(value = """
            SELECT checkin.id
            FROM festival_checkins checkin
            JOIN festivals festival ON festival.id = checkin.festival_id
            WHERE checkin.member_id = :memberId
              AND checkin.festival_id = :festivalId
              AND checkin.status = 'ACTIVE'
              AND LEAST(checkin.expires_at, checkin.checked_in_at + INTERVAL '1 hour') > :now
              AND festival.status = 'ACTIVE'
            ORDER BY checkin.checked_in_at DESC, checkin.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Long> findValidCheckinId(
            @Param("memberId") long memberId,
            @Param("festivalId") long festivalId,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
            SELECT pool.* FROM match_pools pool
            JOIN festival_checkins checkin ON checkin.id = pool.checkin_id
            WHERE pool.status = 'WAITING'
              AND pool.search_expires_at > :now
              AND checkin.member_id = pool.member_id
              AND checkin.festival_id = pool.festival_id
              AND checkin.status = 'ACTIVE'
              AND LEAST(checkin.expires_at, checkin.checked_in_at + INTERVAL '1 hour') > :now
              AND NOT EXISTS (
                  SELECT 1 FROM match_cooldowns cooldown
                  WHERE cooldown.member_id = pool.member_id
                    AND cooldown.status = 'ACTIVE'
                    AND cooldown.starts_at <= :now
                    AND cooldown.expires_at > :now
              )
            ORDER BY pool.entered_at ASC, pool.id ASC
            LIMIT :limit
            FOR UPDATE OF pool SKIP LOCKED
            """, nativeQuery = true)
    List<MatchPool> findSchedulerClaimablePoolsForUpdate(
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT pool.*
            FROM match_pools pool
            JOIN match_pools requester ON requester.id = :requesterPoolId
            JOIN festival_checkins checkin ON checkin.id = pool.checkin_id
            WHERE requester.member_id = :requesterMemberId
              AND requester.festival_id = :festivalId
              AND requester.status = 'WAITING'
              AND requester.search_expires_at > :now
              AND pool.festival_id = requester.festival_id
              AND pool.preferred_group_size = requester.preferred_group_size
              AND pool.status = 'WAITING'
              AND pool.search_expires_at > :now
              AND checkin.member_id = pool.member_id
              AND checkin.festival_id = pool.festival_id
              AND checkin.status = 'ACTIVE'
              AND LEAST(checkin.expires_at, checkin.checked_in_at + INTERVAL '1 hour') > :now
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_cooldowns cooldown
                  WHERE cooldown.member_id = pool.member_id
                    AND cooldown.status = 'ACTIVE'
                    AND cooldown.starts_at <= :now
                    AND cooldown.expires_at > :now
              )
              AND (
                  pool.id = requester.id
                  OR NOT EXISTS (
                      SELECT 1
                      FROM user_blocks block
                      WHERE (block.blocker_member_id = :requesterMemberId
                             AND block.blocked_member_id = pool.member_id)
                         OR (block.blocker_member_id = pool.member_id
                             AND block.blocked_member_id = :requesterMemberId)
                  )
              )
              AND (
                  pool.id = requester.id
                  OR NOT EXISTS (
                      SELECT 1
                      FROM match_opponent_exclusions exclusion
                      WHERE exclusion.lower_member_id = LEAST(requester.member_id, pool.member_id)
                        AND exclusion.higher_member_id = GREATEST(requester.member_id, pool.member_id)
                        AND exclusion.lower_checkin_id = CASE
                            WHEN requester.member_id < pool.member_id THEN requester.checkin_id
                            ELSE pool.checkin_id
                        END
                        AND exclusion.higher_checkin_id = CASE
                            WHEN requester.member_id < pool.member_id THEN pool.checkin_id
                            ELSE requester.checkin_id
                        END
                  )
              )
            ORDER BY CASE WHEN pool.id = :requesterPoolId THEN 0 ELSE 1 END,
                     pool.entered_at ASC,
                     pool.id ASC
            LIMIT :limit
            FOR UPDATE OF pool SKIP LOCKED
            """, nativeQuery = true)
    List<MatchPool> findPoolEntryClaimablePoolsForUpdate(
            @Param("requesterPoolId") long requesterPoolId,
            @Param("requesterMemberId") long requesterMemberId,
            @Param("festivalId") long festivalId,
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );

    List<MatchPool> findAllByLockTokenOrderByEnteredAtAscIdAsc(String lockToken);

    @Query(value = "SELECT * FROM match_pools WHERE id IN (:poolIds) ORDER BY id FOR UPDATE", nativeQuery = true)
    List<MatchPool> findResponsePoolsForUpdate(@Param("poolIds") List<Long> poolIds);

    @Query(value = """
            SELECT * FROM match_pools
            WHERE id IN (:poolIds)
            ORDER BY id ASC
            FOR UPDATE
            """, nativeQuery = true)
    List<MatchPool> findAllByIdsForUpdate(@Param("poolIds") List<Long> poolIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE match_pools
            SET status = CASE WHEN search_expires_at <= :now THEN 'EXPIRED' ELSE 'WAITING' END,
                locked_at = NULL, lock_token = NULL, updated_at = :now
            WHERE status = 'LOCKED' AND lock_token = :lockToken
            """, nativeQuery = true)
    int releaseOwnedLockedPools(@Param("lockToken") String lockToken, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE match_pools
            SET status = 'EXPIRED', updated_at = :now
            WHERE status = 'WAITING'
              AND search_expires_at <= :now
            """, nativeQuery = true)
    int expireWaitingPools(@Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE match_pools
            SET status = 'EXPIRED', locked_at = NULL, lock_token = NULL, updated_at = :now
            WHERE status = 'LOCKED'
              AND locked_at IS NOT NULL
              AND lock_token IS NOT NULL
              AND locked_at <= :staleBefore
              AND search_expires_at <= :now
            """, nativeQuery = true)
    int expireStaleLockedPools(
            @Param("now") OffsetDateTime now,
            @Param("staleBefore") OffsetDateTime staleBefore
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE match_pools
            SET status = 'WAITING', locked_at = NULL, lock_token = NULL, updated_at = :now
            WHERE status = 'LOCKED'
              AND locked_at IS NOT NULL
              AND lock_token IS NOT NULL
              AND locked_at <= :staleBefore
              AND search_expires_at > :now
            """, nativeQuery = true)
    int releaseStaleLockedPools(
            @Param("now") OffsetDateTime now,
            @Param("staleBefore") OffsetDateTime staleBefore
    );

    // 회원이 다른 축제로 재체크인해 기존 체크인이 취소됐을 때, 그 축제에 남은 이 회원의
    // WAITING pool을 정리한다. LOCKED/PROPOSED는 이미 매칭 시도가 진행 중일 수 있어 건드리지
    // 않는다(docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md 4.4절).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE match_pools
            SET status = 'CANCELLED', updated_at = :now
            WHERE member_id = :memberId
              AND festival_id = :festivalId
              AND status = 'WAITING'
            """, nativeQuery = true)
    int cancelWaitingPool(
            @Param("memberId") long memberId,
            @Param("festivalId") long festivalId,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
            SELECT pool.*
            FROM match_pools pool
            JOIN match_pools requester
              ON requester.member_id = :requesterMemberId
             AND requester.festival_id = :festivalId
             AND requester.status IN ('WAITING', 'LOCKED')
            JOIN festival_checkins checkin ON checkin.id = pool.checkin_id
            WHERE pool.festival_id = :festivalId
              AND pool.member_id <> :requesterMemberId
              AND pool.status = 'WAITING'
              AND pool.search_expires_at > :now
              AND checkin.member_id = pool.member_id
              AND checkin.festival_id = pool.festival_id
              AND checkin.status = 'ACTIVE'
              AND LEAST(checkin.expires_at, checkin.checked_in_at + INTERVAL '1 hour') > :now
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_cooldowns cooldown
                  WHERE cooldown.member_id = pool.member_id
                    AND cooldown.status = 'ACTIVE'
                    AND cooldown.starts_at <= :now
                    AND cooldown.expires_at > :now
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_blocks block
                  WHERE (block.blocker_member_id = :requesterMemberId
                         AND block.blocked_member_id = pool.member_id)
                     OR (block.blocker_member_id = pool.member_id
                         AND block.blocked_member_id = :requesterMemberId)
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_opponent_exclusions exclusion
                  WHERE exclusion.lower_member_id = LEAST(requester.member_id, pool.member_id)
                    AND exclusion.higher_member_id = GREATEST(requester.member_id, pool.member_id)
                    AND exclusion.lower_checkin_id = CASE
                        WHEN requester.member_id < pool.member_id THEN requester.checkin_id
                        ELSE pool.checkin_id
                    END
                    AND exclusion.higher_checkin_id = CASE
                        WHEN requester.member_id < pool.member_id THEN pool.checkin_id
                        ELSE requester.checkin_id
                    END
              )
            ORDER BY pool.entered_at ASC, pool.id ASC
            """, nativeQuery = true)
    List<MatchPool> findEligibleWaitingCandidates(
            @Param("festivalId") Long festivalId,
            @Param("requesterMemberId") Long requesterMemberId,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
            SELECT pool.*
            FROM match_pools pool
            JOIN match_pools requester
              ON requester.member_id = :requesterMemberId
             AND requester.festival_id = :festivalId
             AND requester.status IN ('WAITING', 'LOCKED')
            JOIN festival_checkins checkin ON checkin.id = pool.checkin_id
            WHERE pool.festival_id = :festivalId
              AND pool.member_id <> :requesterMemberId
              AND pool.status = 'WAITING'
              AND pool.search_expires_at > :now
              AND checkin.member_id = pool.member_id
              AND checkin.festival_id = pool.festival_id
              AND checkin.status = 'ACTIVE'
              AND LEAST(checkin.expires_at, checkin.checked_in_at + INTERVAL '1 hour') > :now
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_cooldowns cooldown
                  WHERE cooldown.member_id = pool.member_id
                    AND cooldown.status = 'ACTIVE'
                    AND cooldown.starts_at <= :now
                    AND cooldown.expires_at > :now
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_blocks block
                  WHERE (block.blocker_member_id = :requesterMemberId
                         AND block.blocked_member_id = pool.member_id)
                     OR (block.blocker_member_id = pool.member_id
                         AND block.blocked_member_id = :requesterMemberId)
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_opponent_exclusions exclusion
                  WHERE exclusion.lower_member_id = LEAST(requester.member_id, pool.member_id)
                    AND exclusion.higher_member_id = GREATEST(requester.member_id, pool.member_id)
                    AND exclusion.lower_checkin_id = CASE
                        WHEN requester.member_id < pool.member_id THEN requester.checkin_id
                        ELSE pool.checkin_id
                    END
                    AND exclusion.higher_checkin_id = CASE
                        WHEN requester.member_id < pool.member_id THEN pool.checkin_id
                        ELSE requester.checkin_id
                    END
              )
            ORDER BY pool.entered_at ASC, pool.id ASC
            LIMIT :limit
            FOR UPDATE OF pool SKIP LOCKED
            """, nativeQuery = true)
    List<MatchPool> findEligibleWaitingCandidatesForUpdate(
            @Param("festivalId") Long festivalId,
            @Param("requesterMemberId") Long requesterMemberId,
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT pool.* FROM match_pools pool
            WHERE pool.member_id = :memberId
              AND pool.status IN ('WAITING', 'LOCKED', 'PROPOSED')
            ORDER BY pool.id DESC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<MatchPool> findActiveCancellablePoolForUpdate(@Param("memberId") long memberId);
}
