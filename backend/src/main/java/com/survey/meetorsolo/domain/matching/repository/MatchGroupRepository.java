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
