package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import java.util.List;
import java.util.Optional;
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
                CASE WHEN member.status = 'WITHDRAWN' THEN '탈퇴한 회원'
                     ELSE member.nickname END AS nickname,
                member.profile_image_url AS profileImageUrl,
                group_member.status AS status,
                group_member.arrival_minutes AS arrivalMinutes,
                group_member.arrival_time_selected_at AS arrivalTimeSelectedAt,
                group_member.arrived_at AS arrivedAt
            FROM match_group_members group_member
            JOIN members member ON member.id = group_member.member_id
            WHERE group_member.group_id = :groupId
              AND group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
            ORDER BY group_member.id
            """, nativeQuery = true)
    List<ActiveGroupMemberProjection> findActiveMembersWithProfileByGroupId(
            @Param("groupId") long groupId
    );

    @Query(value = """
            SELECT
                group_member.id AS groupMemberId,
                member.id AS memberId,
                CASE WHEN member.status = 'WITHDRAWN' THEN '탈퇴한 회원'
                     ELSE member.nickname END AS nickname,
                member.profile_image_url AS profileImageUrl,
                group_member.status AS status,
                group_member.arrival_minutes AS arrivalMinutes,
                group_member.arrival_time_selected_at AS arrivalTimeSelectedAt,
                group_member.arrived_at AS arrivedAt
            FROM match_group_members group_member
            JOIN members member ON member.id = group_member.member_id
            WHERE group_member.group_id = :groupId
              AND group_member.status = 'COMPLETED'
            ORDER BY group_member.id
            """, nativeQuery = true)
    List<ActiveGroupMemberProjection> findCompletedMembersWithProfileByGroupId(
            @Param("groupId") long groupId
    );

    @Query(value = """
            SELECT *
            FROM match_group_members
            WHERE group_id = :groupId
              AND member_id = :memberId
            FOR UPDATE
            """, nativeQuery = true)
    Optional<MatchGroupMember> findByGroupIdAndMemberIdForUpdate(
            @Param("groupId") long groupId,
            @Param("memberId") long memberId
    );

    @Query(value = """
            SELECT * FROM match_group_members
            WHERE group_id = :groupId
            ORDER BY id
            FOR UPDATE
            """, nativeQuery = true)
    List<MatchGroupMember> findAllByGroupIdForUpdate(@Param("groupId") long groupId);

    @Query(value = """
            SELECT group_member.*
            FROM match_group_members group_member
            JOIN match_groups matching_group ON matching_group.id = group_member.group_id
            WHERE group_member.member_id = :memberId
              AND group_member.status = 'CANCELLED'
              AND matching_group.confirmed_at + INTERVAL '30 minutes' > :now
            ORDER BY group_member.cancelled_at DESC, group_member.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<MatchGroupMember> findLatestCancelledByMemberId(
            @Param("memberId") long memberId,
            @Param("now") java.time.OffsetDateTime now
    );

    @Query(value = """
            SELECT member_id
            FROM match_group_members
            WHERE group_id = :groupId
              AND status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
            ORDER BY id
            """, nativeQuery = true)
    List<Long> findActiveMemberIdsByGroupId(@Param("groupId") long groupId);

    /**
     * 매칭 기록 목록에 붙일 상대 참가자다. 본인은 신고 대상이 아니므로 조회 단계에서 제외한다.
     *
     * <p>{@code findCompletedMembersWithProfileByGroupId}는 {@code status = 'COMPLETED'}로 좁혀져
     * 있어 취소된 그룹의 참가자를 담지 못한다. 이력 열람은 상태와 무관하게 함께 있었던 사람을
     * 보여줘야 하므로 별도 조회를 둔다.
     */
    @Query(value = """
            SELECT
                group_member.group_id AS groupId,
                member.id AS memberId,
                CASE WHEN member.status = 'WITHDRAWN' THEN '탈퇴한 회원'
                     ELSE member.nickname END AS nickname,
                member.profile_image_url AS profileImageUrl
            FROM match_group_members group_member
            JOIN members member ON member.id = group_member.member_id
            WHERE group_member.group_id IN (:groupIds)
              AND group_member.member_id <> :excludedMemberId
            ORDER BY group_member.group_id, group_member.id
            """, nativeQuery = true)
    List<MatchHistoryMemberProjection> findHistoryMembersByGroupIds(
            @Param("groupIds") List<Long> groupIds,
            @Param("excludedMemberId") long excludedMemberId
    );

    interface MatchHistoryMemberProjection {
        Long getGroupId();
        Long getMemberId();
        String getNickname();
        String getProfileImageUrl();
    }

    interface ActiveGroupMemberProjection {
        Long getGroupMemberId();
        Long getMemberId();
        String getNickname();
        String getProfileImageUrl();
        String getStatus();
        Integer getArrivalMinutes();
        java.time.Instant getArrivalTimeSelectedAt();
        java.time.Instant getArrivedAt();
    }
}
