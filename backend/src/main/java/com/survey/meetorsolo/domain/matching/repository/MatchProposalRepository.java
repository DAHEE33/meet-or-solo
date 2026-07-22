package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, Long> {
    @Query(value="SELECT * FROM match_proposals WHERE id=:proposalId FOR UPDATE", nativeQuery=true)
    Optional<MatchProposal> findByIdForUpdate(@Param("proposalId") long proposalId);
    List<MatchProposal> findAllByAttemptIdOrderByIdAsc(long attemptId);
    boolean existsByAttemptIdAndProposalRound(long attemptId, int proposalRound);
    Optional<MatchProposal> findFirstByAttemptIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            long attemptId, String status, java.time.OffsetDateTime now);
}
