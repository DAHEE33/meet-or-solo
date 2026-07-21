package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, Long> { }
