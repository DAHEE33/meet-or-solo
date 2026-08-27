package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.dto.FestivalSummary;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
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
     * 목록/검색 화면 전용 — {@code raw_data} JSONB 등 목록에 쓰이지 않는 컬럼은 읽지
     * 않도록 {@link FestivalSummary} 프로젝션으로 반환한다(성능 개선, 엔티티 전체 로딩 아님).
     *
     * <p>{@code scheduleStart}/{@code scheduleEnd}는 일정 필터의 날짜 구간이다. 축제 기간과
     * 겹치는지로 판단하며, 날짜가 {@code null}인 축제는 열린 구간으로 취급해 배제하지 않는다.
     *
     * <p>{@code requireMeetingPoint}는 boolean이 아니라 {@code int}(0/1)다. JPQL에서 boolean
     * 파라미터를 리터럴과 비교하면 드라이버·방언에 따라 타입 추론이 흔들릴 수 있어, 이미
     * {@code keyword}에서 겪은 것과 같은 종류의 문제를 피하려고 정수 플래그를 쓴다.
     */
    @Query("""
            select new com.survey.meetorsolo.domain.festival.dto.FestivalSummary(
                festival.id, festival.contentId, festival.title, festival.address,
                festival.areaCode, festival.sigunguCode, festival.eventStartDate,
                festival.eventEndDate, festival.status, festival.mapX, festival.mapY)
            from Festival festival
            where festival.status = :status
              and (festival.eventEndDate is null or festival.eventEndDate >= :today)
              and lower(festival.title) like lower(concat('%', :keyword, '%'))
              and (:sigunguCode is null or festival.sigunguCode = :sigunguCode)
              and (festival.eventStartDate is null or festival.eventStartDate <= :scheduleEnd)
              and (festival.eventEndDate is null or festival.eventEndDate >= :scheduleStart)
              and (:requireMeetingPoint = 0 or exists (
                      select 1 from FestivalMeetingPoint point
                      where point.festivalId = festival.id
                        and point.status = :meetingPointStatus
                  ))
            """)
    Page<FestivalSummary> findVisibleFestivals(
            @Param("status") FestivalStatus status,
            @Param("today") LocalDate today,
            @Param("keyword") String keyword,
            @Param("sigunguCode") String sigunguCode,
            @Param("scheduleStart") LocalDate scheduleStart,
            @Param("scheduleEnd") LocalDate scheduleEnd,
            @Param("requireMeetingPoint") int requireMeetingPoint,
            @Param("meetingPointStatus") FestivalMeetingPointStatus meetingPointStatus,
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
