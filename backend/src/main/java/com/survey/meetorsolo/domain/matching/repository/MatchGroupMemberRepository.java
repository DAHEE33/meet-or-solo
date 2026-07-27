package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import java.util.List;
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

    @Query(value = """
            SELECT
                group_member.id AS groupMemberId,
                member.id AS memberId,
                member.nickname AS nickname,
                member.profile_image_url AS profileImageUrl
            FROM match_group_members group_member
            JOIN members member ON member.id = group_member.member_id
            WHERE group_member.group_id = :groupId
              AND group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
            ORDER BY group_member.id
            """, nativeQuery = true)
    List<ActiveGroupMemberProjection> findActiveMembersWithProfileByGroupId(
            @Param("groupId") long groupId
    );

    interface ActiveGroupMemberProjection {
        Long getGroupMemberId();
        Long getMemberId();
        String getNickname();
        String getProfileImageUrl();
    }
}
