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
                matching_group.confirmed_at AS confirmedAt,
                matching_group.started_at AS startedAt,
                festival.title AS festivalTitle,
                festival.address AS festivalAddress,
                festival.event_start_date AS festivalEventStartDate,
                festival.event_end_date AS festivalEventEndDate
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
            JOIN match_group_members group_member
              ON group_member.group_id = matching_group.id
            WHERE group_member.member_id = :memberId
              AND group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
              AND matching_group.status IN ('CONFIRMED', 'IN_PROGRESS')
            ORDER BY matching_group.id
            FOR UPDATE OF matching_group
            """, nativeQuery = true)
    List<MatchGroup> findActiveByMemberIdForUpdate(@Param("memberId") long memberId);

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
    }
}
