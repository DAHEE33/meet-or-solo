package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchResponse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResponseRepository extends JpaRepository<MatchResponse, Long> {
    Optional<MatchResponse> findByProposalIdAndMemberId(long proposalId, long memberId);
}
