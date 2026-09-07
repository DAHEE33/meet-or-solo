package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchGroupRepository extends JpaRepository<MatchGroup, Long> {

    @Query(value = """
            SELECT
                matching_group.id AS groupId,
                matching_group.festival_id AS festivalId,
                matching_group.status AS status,
                matching_group.confirmed_member_count AS confirmedMemberCount,
                matching_group.meeting_place_name AS meetingPlaceName,
                matching_group.meeting_place_address AS meetingPlaceAddress,
                matching_group.meeting_place_content_id AS meetingPlaceContentId,
                matching_group.meeting_map_x AS meetingMapX,
                matching_group.meeting_map_y AS meetingMapY,
                matching_group.confirmed_at AS confirmedAt,
                matching_group.started_at AS startedAt,
                matching_group.completed_at AS completedAt,
                festival.title AS festivalTitle,
                festival.address AS festivalAddress,
                festival.event_start_date AS festivalEventStartDate,
                festival.event_end_date AS festivalEventEndDate,
                festival.meeting_radius_meters AS meetingRadiusMeters
            FROM match_groups matching_group
            JOIN match_group_members group_member
              ON group_member.group_id = matching_group.id
            JOIN festivals festival
              ON festival.id = matching_group.festival_id
            WHERE group_member.member_id = :memberId
              AND group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
              AND matching_group.status IN ('CONFIRMED', 'IN_PROGRESS')
            ORDER BY matching_group.id
            """, nativeQuery = true)
    List<ActiveGroupWithFestivalProjection> findActiveByMemberId(@Param("memberId") long memberId);

    @Query(value = """
            SELECT matching_group.*
            FROM match_groups matching_group
            JOIN match_group_members group_member ON group_member.group_id = matching_group.id
            WHERE group_member.member_id = :memberId
              AND group_member.status = 'COMPLETED'
              AND matching_group.status = 'COMPLETED'
            ORDER BY matching_group.completed_at DESC, matching_group.id DESC
            LIMIT 1
            FOR UPDATE OF matching_group
            """, nativeQuery = true)
    java.util.Optional<MatchGroup> findLatestCompletedByMemberIdForUpdate(
            @Param("memberId") long memberId
    );

    /**
     * 매칭 기록 목록이다. 신고 진입점(docs/19 4.10)이 쓰는 조회로, 정상 종료와 취소를 모두 담는다.
     *
     * <p>완료 판정에 쓰는 {@code findLatestCompletedByMemberId}와 목적이 다르다. 그쪽은 최신 1건의
     * 완료 여부를 보는 것이고 이 조회는 이력 열람이므로 조건을 공유하지 않는다.
     *
     * <p>cursor는 (종료 시각, group id) 복합이다. 종료 시각이 같은 행이 있어도 순서가 흔들리지
     * 않도록 id를 tiebreaker로 함께 비교한다.
     */
    @Query(value = """
            SELECT
                matching_group.id AS groupId,
                matching_group.status AS status,
                matching_group.confirmed_member_count AS confirmedMemberCount,
                matching_group.meeting_place_name AS meetingPlaceName,
                COALESCE(matching_group.completed_at, matching_group.cancelled_at) AS endedAt,
                festival.title AS festivalTitle,
                festival.address AS festivalAddress
            FROM match_groups matching_group
            JOIN match_group_members group_member
              ON group_member.group_id = matching_group.id
            JOIN festivals festival
              ON festival.id = matching_group.festival_id
            WHERE group_member.member_id = :memberId
              AND matching_group.status IN ('COMPLETED', 'CANCELLED')
              AND COALESCE(matching_group.completed_at, matching_group.cancelled_at) IS NOT NULL
              AND (
                  CAST(:cursorEndedAt AS timestamptz) IS NULL
                  OR (
                      COALESCE(matching_group.completed_at, matching_group.cancelled_at),
                      matching_group.id
                  ) < (CAST(:cursorEndedAt AS timestamptz), :cursorGroupId)
              )
            ORDER BY COALESCE(matching_group.completed_at, matching_group.cancelled_at) DESC,
                     matching_group.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<MatchHistoryGroupProjection> findHistoryByMemberId(
            @Param("memberId") long memberId,
            @Param("cursorEndedAt") java.time.OffsetDateTime cursorEndedAt,
            @Param("cursorGroupId") long cursorGroupId,
            @Param("limit") int limit
    );

    interface MatchHistoryGroupProjection {
        Long getGroupId();
        String getStatus();
        Integer getConfirmedMemberCount();
        String getMeetingPlaceName();
        java.time.Instant getEndedAt();
        String getFestivalTitle();
        String getFestivalAddress();
    }

    @Query(value = """
            SELECT matching_group.*
            FROM match_groups matching_group
            JOIN match_group_members group_member ON group_member.group_id = matching_group.id
            WHERE group_member.member_id = :memberId
              AND group_member.status = 'COMPLETED'
              AND matching_group.status = 'COMPLETED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_pools newer_pool
                  WHERE newer_pool.member_id = :memberId
                    AND newer_pool.entered_at > matching_group.confirmed_at
              )
            ORDER BY matching_group.completed_at DESC, matching_group.id DESC
            LIMIT 1
            """, nativeQuery = true)
    java.util.Optional<MatchGroup> findLatestCompletedByMemberId(@Param("memberId") long memberId);

    @Query(value = """
            SELECT
                matching_group.id AS groupId,
                matching_group.festival_id AS festivalId,
                matching_group.status AS status,
                matching_group.confirmed_member_count AS confirmedMemberCount,
                matching_group.meeting_place_name AS meetingPlaceName,
                matching_group.meeting_place_address AS meetingPlaceAddress,
                matching_group.meeting_place_content_id AS meetingPlaceContentId,
                matching_group.meeting_map_x AS meetingMapX,
                matching_group.meeting_map_y AS meetingMapY,
                matching_group.confirmed_at AS confirmedAt,
                matching_group.started_at AS startedAt,
                matching_group.completed_at AS completedAt,
                festival.title AS festivalTitle,
                festival.address AS festivalAddress,
                festival.event_start_date AS festivalEventStartDate,
                festival.event_end_date AS festivalEventEndDate,
                festival.meeting_radius_meters AS meetingRadiusMeters
            FROM match_groups matching_group
            JOIN festivals festival ON festival.id = matching_group.festival_id
            WHERE matching_group.id = :groupId
            """, nativeQuery = true)
    java.util.Optional<ActiveGroupWithFestivalProjection> findSnapshotById(
            @Param("groupId") long groupId
    );

    @Query(value = """
            SELECT count(*) FROM match_groups
            WHERE festival_id = :festivalId
              AND meeting_place_content_id IS NOT NULL
            """, nativeQuery = true)
    long countAssignedMeetingPointGroups(@Param("festivalId") long festivalId);

    @Query(value = """
            SELECT matching_group.*
            FROM match_groups matching_group
            JOIN match_group_members group_member
              ON group_member.group_id = matching_group.id
            WHERE group_member.member_id = :memberId
              AND group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
              AND matching_group.status IN ('CONFIRMED', 'IN_PROGRESS')
            ORDER BY matching_group.id
            FOR UPDATE OF matching_group
            """, nativeQuery = true)
    List<MatchGroup> findActiveByMemberIdForUpdate(@Param("memberId") long memberId);

    @Query(value = """
            SELECT * FROM match_groups
            WHERE id = :groupId
            FOR UPDATE
            """, nativeQuery = true)
    java.util.Optional<MatchGroup> findByIdForUpdate(@Param("groupId") long groupId);

    @Query(value = """
            SELECT * FROM match_groups
            WHERE id = :groupId
              AND status IN ('CONFIRMED', 'IN_PROGRESS')
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    java.util.Optional<MatchGroup> tryLockActiveById(@Param("groupId") long groupId);

    @Query(value = """
            SELECT matching_group.id
            FROM match_groups matching_group
            WHERE matching_group.status IN ('CONFIRMED', 'IN_PROGRESS')
              AND matching_group.confirmed_at + INTERVAL '30 minutes' <= :now
              AND EXISTS (
                  SELECT 1 FROM match_group_members group_member
                  WHERE group_member.group_id = matching_group.id
                    AND group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED')
              )
            ORDER BY matching_group.confirmed_at, matching_group.id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Long> findNoShowCandidateIds(
            @Param("now") java.time.OffsetDateTime now,
            @Param("batchSize") int batchSize
    );

    interface ActiveGroupWithFestivalProjection {
        Long getGroupId();
        Long getFestivalId();
        String getStatus();
        Integer getConfirmedMemberCount();
        Instant getConfirmedAt();
        Instant getStartedAt();
        Instant getCompletedAt();
        String getFestivalTitle();
        String getFestivalAddress();
        LocalDate getFestivalEventStartDate();
        LocalDate getFestivalEventEndDate();
        String getMeetingPlaceName();
        String getMeetingPlaceAddress();
        String getMeetingPlaceContentId();
        java.math.BigDecimal getMeetingMapX();
        java.math.BigDecimal getMeetingMapY();
        Integer getMeetingRadiusMeters();
    }
}
