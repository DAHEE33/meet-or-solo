package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.dto.FestivalSummary;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.global.region.RegionAggregate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalRepository extends JpaRepository<Festival, Long> {

    List<Festival> findAllByContentIdIn(Collection<String> contentIds);

    Optional<Festival> findByContentId(String contentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Festival festival
            set festival.status = :endedStatus,
                festival.updatedAt = :updatedAt
            where festival.eventEndDate < :syncDate
              and festival.status <> :endedStatus
              and festival.status <> :hiddenStatus
            """)
    int markEndedBefore(
            @Param("syncDate") LocalDate syncDate,
            @Param("endedStatus") FestivalStatus endedStatus,
            @Param("hiddenStatus") FestivalStatus hiddenStatus,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Festival festival
            set festival.status = :inactiveStatus,
                festival.updatedAt = :updatedAt
            where festival.status = :activeStatus
              and festival.eventStartDate between :eventStartDate and :eventEndDate
              and festival.areaCode = :regionCode
              and festival.contentId not in :observedContentIds
            """)
    int markActiveMissingInScopeInactive(
            @Param("observedContentIds") Collection<String> observedContentIds,
            @Param("eventStartDate") LocalDate eventStartDate,
            @Param("eventEndDate") LocalDate eventEndDate,
            @Param("regionCode") String regionCode,
            @Param("activeStatus") FestivalStatus activeStatus,
            @Param("inactiveStatus") FestivalStatus inactiveStatus,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Festival festival
            set festival.status = :inactiveStatus,
                festival.updatedAt = :updatedAt
            where festival.status = :activeStatus
              and festival.eventStartDate between :eventStartDate and :eventEndDate
              and festival.areaCode = :regionCode
            """)
    int markAllActiveInScopeInactive(
            @Param("eventStartDate") LocalDate eventStartDate,
            @Param("eventEndDate") LocalDate eventEndDate,
            @Param("regionCode") String regionCode,
            @Param("activeStatus") FestivalStatus activeStatus,
            @Param("inactiveStatus") FestivalStatus inactiveStatus,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    /**
     * 목록/검색 화면 전용. 좋아요(찜)·후기(댓글) 수를 함께 집계하고 그 값으로 정렬까지 하므로
     * native query다 — 집계 값은 엔티티 속성이 아니라서 {@code Pageable}의 {@code Sort}로
     * 표현할 수 없고, 정렬 키를 응용 계층에서 계산하면 페이지 경계가 어긋난다.
     *
     * <p>집계를 파생 테이블 안에서 한 번만 계산하고 바깥에서 정렬한다. PostgreSQL은
     * {@code ORDER BY} 식 안의 이름을 출력 별칭이 아니라 입력 컬럼으로 해석하므로, 감싸지
     * 않으면 같은 서브쿼리를 {@code ORDER BY}에 한 번 더 써야 한다.
     *
     * <p><b>가시성</b>: {@code includeEnded = 0}이면 기존과 같이 {@code ACTIVE}이면서 종료일이
     * 지나지 않은 축제만 보인다. {@code 1}이면 {@code ENDED}까지 포함하고 종료일 컷을 푼다 —
     * "진행 마감" 검색용이며, 호출부가 {@code progress}를 명시했을 때만 켠다
     * (docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md).
     *
     * <p><b>기간</b>: {@code scheduleStart}/{@code scheduleEnd}는 각각 선택이며 축제 기간과
     * 겹치는지로 판단한다. 날짜가 {@code null}인 축제(동기화 데이터 불완전)는 열린 구간으로
     * 취급해 배제하지 않는다.
     *
     * <p><b>진행 상태</b>: 판정 규칙을 frontend {@code resolveDisplayStatus}와 일치시킨다.
     * {@code ENDED} 상태는 날짜와 무관하게 마감이다.
     *
     * <p>{@code CAST(... AS date)}가 붙은 자리는 null 바인딩의 타입을 PostgreSQL이 추론하지
     * 못해 필요한 것이고, {@code requireMeetingPoint}가 boolean이 아니라 {@code int}(0/1)인
     * 것도 같은 종류의 타입 추론 문제를 피하기 위해서다.
     */
    @Query(value = """
            SELECT * FROM (
                SELECT
                    festival.id AS id,
                    festival.content_id AS contentId,
                    festival.title AS title,
                    festival.address AS address,
                    festival.area_code AS regionCode,
                    festival.sigungu_code AS sigunguCode,
                    festival.event_start_date AS eventStartDate,
                    festival.event_end_date AS eventEndDate,
                    festival.status AS status,
                    festival.map_x AS mapX,
                    festival.map_y AS mapY,
                    festival.created_at AS createdAt,
                    (SELECT COUNT(*) FROM content_bookmarks bookmark
                      WHERE bookmark.festival_id = festival.id) AS bookmarkCount,
                    (SELECT COUNT(*) FROM content_comments comment
                      WHERE comment.festival_id = festival.id
                        AND comment.status = 'VISIBLE') AS commentCount
                FROM festivals festival
                WHERE (
                        (:includeEnded = 1 AND festival.status IN ('ACTIVE', 'ENDED'))
                     OR (:includeEnded = 0 AND festival.status = 'ACTIVE'
                         AND (festival.event_end_date IS NULL OR festival.event_end_date >= :today))
                  )
                  AND lower(festival.title) LIKE lower(concat('%', :keyword, '%'))
                  AND (CAST(:sigunguCode AS varchar) IS NULL OR festival.sigungu_code = :sigunguCode)
                  AND (CAST(:scheduleStart AS date) IS NULL
                       OR festival.event_end_date IS NULL
                       OR festival.event_end_date >= CAST(:scheduleStart AS date))
                  AND (CAST(:scheduleEnd AS date) IS NULL
                       OR festival.event_start_date IS NULL
                       OR festival.event_start_date <= CAST(:scheduleEnd AS date))
                  AND (
                        :progress = 'ALL'
                     OR (:progress = 'UPCOMING'
                         AND festival.status <> 'ENDED'
                         AND festival.event_start_date IS NOT NULL
                         AND festival.event_start_date > :today)
                     OR (:progress = 'ONGOING'
                         AND festival.status <> 'ENDED'
                         AND (festival.event_start_date IS NULL OR festival.event_start_date <= :today)
                         AND (festival.event_end_date IS NULL OR festival.event_end_date >= :today))
                     OR (:progress = 'ENDED'
                         AND (festival.status = 'ENDED'
                              OR (festival.event_end_date IS NOT NULL
                                  AND festival.event_end_date < :today)))
                  )
                  AND (:requireMeetingPoint = 0 OR EXISTS (
                          SELECT 1 FROM festival_meeting_points point
                          WHERE point.festival_id = festival.id
                            AND point.status = :meetingPointStatus
                      ))
            ) ranked
            ORDER BY
                CASE WHEN :sort = 'BOOKMARK_COUNT_DESC' THEN ranked.bookmarkCount
                     WHEN :sort = 'COMMENT_COUNT_DESC' THEN ranked.commentCount
                     ELSE 0 END DESC,
                CASE WHEN :sort = 'RECENTLY_ADDED' THEN ranked.createdAt END DESC,
                ranked.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM festivals festival
            WHERE (
                    (:includeEnded = 1 AND festival.status IN ('ACTIVE', 'ENDED'))
                 OR (:includeEnded = 0 AND festival.status = 'ACTIVE'
                     AND (festival.event_end_date IS NULL OR festival.event_end_date >= :today))
              )
              AND lower(festival.title) LIKE lower(concat('%', :keyword, '%'))
              AND (CAST(:sigunguCode AS varchar) IS NULL OR festival.sigungu_code = :sigunguCode)
              AND (CAST(:scheduleStart AS date) IS NULL
                   OR festival.event_end_date IS NULL
                   OR festival.event_end_date >= CAST(:scheduleStart AS date))
              AND (CAST(:scheduleEnd AS date) IS NULL
                   OR festival.event_start_date IS NULL
                   OR festival.event_start_date <= CAST(:scheduleEnd AS date))
              AND (
                    :progress = 'ALL'
                 OR (:progress = 'UPCOMING'
                     AND festival.status <> 'ENDED'
                     AND festival.event_start_date IS NOT NULL
                     AND festival.event_start_date > :today)
                 OR (:progress = 'ONGOING'
                     AND festival.status <> 'ENDED'
                     AND (festival.event_start_date IS NULL OR festival.event_start_date <= :today)
                     AND (festival.event_end_date IS NULL OR festival.event_end_date >= :today))
                 OR (:progress = 'ENDED'
                     AND (festival.status = 'ENDED'
                          OR (festival.event_end_date IS NOT NULL
                              AND festival.event_end_date < :today)))
              )
              AND (:requireMeetingPoint = 0 OR EXISTS (
                      SELECT 1 FROM festival_meeting_points point
                      WHERE point.festival_id = festival.id
                        AND point.status = :meetingPointStatus
                  ))
            """,
            nativeQuery = true)
    Page<FestivalListProjection> findVisibleFestivals(
            @Param("includeEnded") int includeEnded,
            @Param("today") LocalDate today,
            @Param("keyword") String keyword,
            @Param("sigunguCode") String sigunguCode,
            @Param("scheduleStart") LocalDate scheduleStart,
            @Param("scheduleEnd") LocalDate scheduleEnd,
            @Param("progress") String progress,
            @Param("sort") String sort,
            @Param("requireMeetingPoint") int requireMeetingPoint,
            @Param("meetingPointStatus") String meetingPointStatus,
            Pageable pageable
    );

    /**
     * {@link #findVisibleFestivals}의 결과 1행. native query라 생성자 표현식을 쓸 수 없어
     * 인터페이스 프로젝션이며, {@code CheckinHistoryProjection}과 같은 방식이다.
     */
    interface FestivalListProjection {
        Long getId();

        String getContentId();

        String getTitle();

        String getAddress();

        String getRegionCode();

        String getSigunguCode();

        LocalDate getEventStartDate();

        LocalDate getEventEndDate();

        /** {@code FestivalStatus} 이름. native query라 enum으로 직접 받지 않는다. */
        String getStatus();

        BigDecimal getMapX();

        BigDecimal getMapY();

        /** 이 축제를 찜한 회원 수. 화면에는 "좋아요 수"로 표시한다. */
        long getBookmarkCount();

        /** 공개({@code VISIBLE}) 댓글 수. 숨김·삭제된 댓글은 세지 않는다. */
        long getCommentCount();
    }

    /**
     * 관리자 만남 장소 화면의 "축제 선택" 검색 전용. {@link #findVisibleFestivals}와 달리
     * {@code eventEndDate}로 종료 여부를 걸러내지 않는다 — 관리자는 방금 끝난 축제의 만남
     * 장소도 조회·수정해야 하기 때문이다(docs/24_ADMIN_MEETING_POINT_MANAGEMENT_DESIGN.md
     * 7장 후속 과제). 대신 {@code statuses}로 노출 범위를 호출부가 직접 정한다 — 관리자
     * 화면은 {@code ACTIVE}/{@code ENDED}만 넘기고, {@code HIDDEN}/{@code INACTIVE}(운영자가
     * 숨겼거나 동기화상 비활성 시즌인 축제)는 제외한다.
     */
    @Query("""
            select new com.survey.meetorsolo.domain.festival.dto.FestivalSummary(
                festival.id, festival.contentId, festival.title, festival.address,
                festival.areaCode, festival.sigunguCode, festival.eventStartDate,
                festival.eventEndDate, festival.status, festival.mapX, festival.mapY)
            from Festival festival
            where festival.status in :statuses
              and lower(festival.title) like lower(concat('%', :keyword, '%'))
            """)
    Page<FestivalSummary> findForAdmin(
            @Param("statuses") Collection<FestivalStatus> statuses,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * 지역 선택 UI용 시군구 집계. 시군구 이름이 DB에 없어 그룹별 대표 주소를 함께 가져오고,
     * 이름은 {@code RegionNameResolver}가 주소 두 번째 토큰에서 뽑는다. 목록 조회와 같은
     * 가시성 조건을 써서 "선택하면 항상 빈 결과인 지역"이 노출되지 않게 한다.
     */
    @Query("""
            select new com.survey.meetorsolo.global.region.RegionAggregate(
                festival.sigunguCode, min(festival.address), count(festival.id))
            from Festival festival
            where festival.status = :status
              and (festival.eventEndDate is null or festival.eventEndDate >= :today)
              and festival.sigunguCode is not null
              and festival.address is not null
            group by festival.sigunguCode
            """)
    List<RegionAggregate> aggregateVisibleRegions(
            @Param("status") FestivalStatus status,
            @Param("today") LocalDate today
    );

    /**
     * 반경 검색(nearby-festivals) 전용 bounding box 사전 필터. 정확한 반경 판정과 정렬은
     * 호출부가 haversine으로 다시 계산하므로, 여기서는 실제 원을 완전히 포함하는 사각 범위만
     * 걸러 후보 수를 줄인다({@code map_x is not null and map_y is not null}은 BETWEEN이 NULL을
     * 자연히 배제하므로 별도 조건이 필요 없다).
     */
    @Query("""
            select festival
            from Festival festival
            where festival.status = :status
              and (festival.eventEndDate is null or festival.eventEndDate >= :today)
              and festival.mapX between :minLongitude and :maxLongitude
              and festival.mapY between :minLatitude and :maxLatitude
            """)
    List<Festival> findAllVisibleWithinBoundingBox(
            @Param("status") FestivalStatus status,
            @Param("today") LocalDate today,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude
    );

    @Query(value = "SELECT * FROM festivals WHERE id = :festivalId FOR UPDATE", nativeQuery = true)
    Optional<Festival> findByIdForUpdate(@Param("festivalId") long festivalId);

    @Query("""
            select festival
            from Festival festival
            where festival.status = :status
              and not exists (
                  select 1 from FestivalMeetingPoint point
                  where point.festivalId = festival.id
              )
            """)
    List<Festival> findAllByStatusWithoutMeetingPoint(@Param("status") FestivalStatus status);
}
