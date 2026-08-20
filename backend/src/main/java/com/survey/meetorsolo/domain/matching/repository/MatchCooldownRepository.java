package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchCooldownRepository extends JpaRepository<MatchCooldown, Long> {

    boolean existsByRelatedProposalId(long relatedProposalId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM match_cooldowns
                WHERE member_id = :memberId
                  AND status = 'ACTIVE'
                  AND starts_at <= :now
                  AND expires_at > :now
            )
            """, nativeQuery = true)
    boolean existsActive(@Param("memberId") long memberId, @Param("now") OffsetDateTime now);

    @Query(value = """
            SELECT * FROM match_cooldowns
            WHERE member_id = :memberId
              AND status = 'ACTIVE'
              AND starts_at <= :now
              AND expires_at > :now
            ORDER BY expires_at DESC, id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<MatchCooldown> findActive(@Param("memberId") long memberId, @Param("now") OffsetDateTime now);

    @Query(value = """
            SELECT * FROM match_cooldowns
            WHERE member_id = :memberId
              AND status = 'ACTIVE'
            ORDER BY id
            FOR UPDATE
            """, nativeQuery = true)
    Optional<MatchCooldown> findActiveForUpdate(@Param("memberId") long memberId);

    boolean existsByRelatedGroupIdAndMemberIdAndReason(
            long relatedGroupId, long memberId, String reason);

    @Modifying
    @Query(value = """
            UPDATE match_cooldowns
            SET status = 'EXPIRED'
            WHERE member_id = :memberId
              AND status = 'ACTIVE'
              AND expires_at <= :now
            """, nativeQuery = true)
    int expireElapsedActive(
            @Param("memberId") long memberId,
            @Param("now") OffsetDateTime now
    );

    boolean existsByRelatedPoolId(long relatedPoolId);

    @Query(value = """
            SELECT count(*) FROM match_cooldowns
            WHERE member_id = :memberId
              AND reason = :reason
              AND created_at >= :dayStart
              AND created_at < :dayEnd
            """, nativeQuery = true)
    int countTodayByReason(
            @Param("memberId") long memberId,
            @Param("reason") String reason,
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("dayEnd") OffsetDateTime dayEnd
    );
}
