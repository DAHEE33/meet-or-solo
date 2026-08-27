package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.dto.FestivalSummary;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
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
     * 목록/검색 화면 전용 — {@code raw_data} JSONB나 좌표 등 목록에 쓰이지 않는 컬럼은 읽지
     * 않도록 {@link FestivalSummary} 프로젝션으로 반환한다(성능 개선, 엔티티 전체 로딩 아님).
     */
    @Query("""
            select new com.survey.meetorsolo.domain.festival.dto.FestivalSummary(
                festival.id, festival.contentId, festival.title, festival.address,
                festival.areaCode, festival.sigunguCode, festival.eventStartDate,
                festival.eventEndDate, festival.status)
            from Festival festival
            where festival.status = :status
              and (festival.eventEndDate is null or festival.eventEndDate >= :today)
              and lower(festival.title) like lower(concat('%', :keyword, '%'))
            """)
    Page<FestivalSummary> findVisibleFestivals(
            @Param("status") FestivalStatus status,
            @Param("today") LocalDate today,
            @Param("keyword") String keyword,
            Pageable pageable
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
