package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchAttemptRepository extends JpaRepository<MatchAttempt, Long> {
    @Query(value = "SELECT * FROM match_attempts WHERE id=:attemptId FOR UPDATE", nativeQuery = true)
    Optional<MatchAttempt> findByIdForUpdate(@Param("attemptId") long attemptId);

    @Query(value = """
            SELECT a.id FROM match_attempts a
            WHERE a.status IN ('WAITING_RESPONSES', 'INSUFFICIENT_MEMBERS')
              AND EXISTS (SELECT 1 FROM match_proposals p WHERE p.attempt_id=a.id
                          AND p.status='SENT' AND p.expires_at<=:now)
            ORDER BY a.expires_at, a.id LIMIT :limit
            """, nativeQuery = true)
    List<Long> findTimeoutCandidateIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);
}
