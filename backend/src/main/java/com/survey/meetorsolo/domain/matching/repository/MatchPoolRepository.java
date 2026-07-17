package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPoolRepository extends JpaRepository<MatchPool, Long> {

    @Query(value = """
            SELECT pool.*
            FROM match_pools pool
            JOIN festival_checkins checkin ON checkin.id = pool.checkin_id
            WHERE pool.festival_id = :festivalId
              AND pool.member_id <> :requesterMemberId
              AND pool.status = 'WAITING'
              AND pool.search_expires_at > :now
              AND checkin.member_id = pool.member_id
              AND checkin.festival_id = pool.festival_id
              AND checkin.status = 'ACTIVE'
              AND checkin.expires_at > :now
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
            ORDER BY pool.entered_at ASC, pool.id ASC
            """, nativeQuery = true)
    List<MatchPool> findEligibleWaitingCandidates(
            @Param("festivalId") Long festivalId,
            @Param("requesterMemberId") Long requesterMemberId,
            @Param("now") OffsetDateTime now
    );
}
