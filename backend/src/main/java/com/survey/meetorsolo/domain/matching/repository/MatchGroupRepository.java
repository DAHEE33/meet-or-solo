package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchGroupRepository extends JpaRepository<MatchGroup, Long> {

    @Query(value = """
            SELECT matching_group.*
            FROM match_groups matching_group
            JOIN match_group_members group_member
              ON group_member.group_id = matching_group.id
            WHERE group_member.member_id = :memberId
              AND group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
              AND matching_group.status IN ('CONFIRMED', 'IN_PROGRESS')
            ORDER BY matching_group.id
            """, nativeQuery = true)
    List<MatchGroup> findActiveByMemberId(@Param("memberId") long memberId);
}
