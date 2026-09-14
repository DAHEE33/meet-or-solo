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
     * <p>완료 판정에 쓰는 {@code findLatestHeldMeetingByMemberId}와 목적이 다르다. 그쪽은 최신 1건의
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

    /**
     * 재매칭 잠금의 기준이 되는 "만남이 성립했던 최근 그룹"이다({@code docs/19} 4.11.3).
     *
     * <p>예전에는 {@code status = 'COMPLETED'}인 그룹만 봤다. 먼저 나가기가 생기면서 그 기준으로는
     * 구멍이 생긴다. 나간 사람은 그룹이 완료되기 전에 이미 빠져나오므로 활성 참여로도 잡히지 않고
     * 완료 참여로도 잡히지 않아, <b>만남을 하고도 곧바로 새 매칭을 신청할 수 있게 된다.</b> 그러면
     * 끝까지 남은 사람만 잠기는 역차별이 되고 "마음에 안 들면 나가서 바로 다시 뽑기"가 가능하다.
     *
     * <p>그래서 기준을 <b>만남이 성립했는가</b>로 바꾼다 — 내가 도착했고, 그 방에 도착자가 2명
     * 이상이면 그룹 상태와 무관하게 잠금 대상이다. 잠금 자체는 시간 기반
     * ({@code MatchCompletionLockPolicy})이라 오래된 그룹은 자연히 만료된다.
     *
     * <p>완료된 그룹의 완료 참여도 그대로 인정한다. 도착 좌표 검증 이전에 쌓인 기록이나 운영 중
     * 손으로 정리한 데이터처럼 {@code arrived_at}이 비어 있는 완료 기록이 있을 수 있는데, 잠금은
     * 안전한 쪽(거는 쪽)으로 기우는 편이 낫다.
     *
     * <p>{@code match_pools}를 보는 조건은 그대로 둔다. 잠금이 풀린 뒤 새로 신청한 사람에게 지난
     * 만남의 잠금이 다시 걸리면 안 된다.
     */
    @Query(value = """
            SELECT matching_group.*
            FROM match_groups matching_group
            JOIN match_group_members group_member ON group_member.group_id = matching_group.id
            WHERE group_member.member_id = :memberId
              AND (
                  (matching_group.status = 'COMPLETED' AND group_member.status = 'COMPLETED')
                  OR (
                      group_member.arrived_at IS NOT NULL
                      AND (
                          SELECT COUNT(*)
                          FROM match_group_members arrived_member
                          WHERE arrived_member.group_id = matching_group.id
                            AND arrived_member.arrived_at IS NOT NULL
                      ) >= 2
                  )
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_pools newer_pool
                  WHERE newer_pool.member_id = :memberId
                    AND newer_pool.entered_at > matching_group.confirmed_at
              )
            ORDER BY matching_group.confirmed_at DESC, matching_group.id DESC
            LIMIT 1
            """, nativeQuery = true)
    java.util.Optional<MatchGroup> findLatestHeldMeetingByMemberId(@Param("memberId") long memberId);

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

    /**
     * 만남 시간이 끝나 닫아야 할 그룹이다({@code MatchMeetingWindowPolicy}).
     *
     * <p>도착자가 한 명이라도 있는 그룹만 찾는다. 아무도 도착하지 않은 그룹은 도착 마감 시점에
     * 노쇼 경로가 이미 취소했으므로 여기서 다시 볼 일이 없다. 간격 {@code 1 hour}는
     * {@code MatchMeetingWindowPolicy.MEETING_WINDOW}와 같아야 한다.
     *
     * <p>도착 여부는 status가 아니라 {@code arrived_at}으로 본다. 먼저 나간 사람은 status가
     * {@code LEFT}로 바뀌지만 그 방에서 만남이 있었다는 사실은 남는다.
     */
    @Query(value = """
            SELECT matching_group.id
            FROM match_groups matching_group
            WHERE matching_group.status IN ('CONFIRMED', 'IN_PROGRESS')
              AND matching_group.confirmed_at + INTERVAL '1 hour' <= :now
              AND EXISTS (
                  SELECT 1 FROM match_group_members group_member
                  WHERE group_member.group_id = matching_group.id
                    AND group_member.arrived_at IS NOT NULL
              )
            ORDER BY matching_group.confirmed_at, matching_group.id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Long> findMeetingCloseCandidateIds(
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
