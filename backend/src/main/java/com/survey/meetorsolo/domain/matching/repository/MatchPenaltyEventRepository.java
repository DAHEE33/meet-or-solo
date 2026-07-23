package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchPenaltyEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchPenaltyEventRepository extends JpaRepository<MatchPenaltyEvent, Long> {

    boolean existsByRelatedProposalId(long relatedProposalId);
}
