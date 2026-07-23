package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchCooldownRepository extends JpaRepository<MatchCooldown, Long> {

    boolean existsByRelatedProposalId(long relatedProposalId);

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
}
