package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchGroupMemberRepository extends JpaRepository<MatchGroupMember, Long> {

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM match_group_members member
                JOIN match_groups matching_group ON matching_group.id = member.group_id
                WHERE member.member_id = :memberId
                  AND member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
                  AND matching_group.status IN ('CONFIRMED', 'IN_PROGRESS')
            )
            """, nativeQuery = true)
    boolean existsActiveByMemberId(@Param("memberId") long memberId);
}
