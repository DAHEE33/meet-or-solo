package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchAttemptRepository extends JpaRepository<MatchAttempt, Long> { }
