package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGroupMemberRepository extends JpaRepository<MatchGroupMember, Long> { }
