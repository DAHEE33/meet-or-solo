package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchAttemptMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MatchAttemptMemberRepository extends JpaRepository<MatchAttemptMember, Long> {
    @Query(value="SELECT * FROM match_attempt_members WHERE attempt_id=:attemptId AND member_id=:memberId FOR UPDATE", nativeQuery=true)
    Optional<MatchAttemptMember> findForUpdate(@Param("attemptId") long attemptId, @Param("memberId") long memberId);
    List<MatchAttemptMember> findAllByAttemptIdOrderByIdAsc(long attemptId);
}
