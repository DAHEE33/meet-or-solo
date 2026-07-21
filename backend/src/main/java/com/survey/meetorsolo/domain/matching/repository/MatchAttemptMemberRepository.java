package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchAttemptMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchAttemptMemberRepository extends JpaRepository<MatchAttemptMember, Long> { }
