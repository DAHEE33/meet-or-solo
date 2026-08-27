package com.survey.meetorsolo.domain.tourplace.repository;

import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListItemResponse;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import java.math.BigDecimal;
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

public interface TourPlaceRepository extends JpaRepository<TourPlace, Long> {

    List<TourPlace> findAllByContentIdIn(Collection<String> contentIds);

    Optional<TourPlace> findByContentId(String contentId);

    long countByContentTypeId(String contentTypeId);

    /**
     * 목록/검색 화면 전용 — {@code raw_data} JSONB, 좌표 등 목록에 쓰이지 않는 컬럼은 읽지
     * 않도록 {@link TourPlaceListItemResponse}를 직접 프로젝션으로 반환한다(성능 개선).
     */
    @Query("""
            select new com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListItemResponse(
                place.id, place.contentId, place.contentTypeId, place.title, place.address,
                place.status, place.imageUrl)
            from TourPlace place
            where place.status = :status
              and (:contentTypeId is null or place.contentTypeId = :contentTypeId)
              and lower(place.title) like lower(concat('%', :keyword, '%'))
            """)
    Page<TourPlaceListItemResponse> findVisiblePlaces(
            @Param("status") TourPlaceStatus status,
            @Param("contentTypeId") String contentTypeId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select place
            from TourPlace place
            where place.status = :status
              and place.mapX is not null
              and place.mapY is not null
            """)
    List<TourPlace> findAllVisibleWithCoordinates(@Param("status") TourPlaceStatus status);

    /**
     * 반경 검색(nearby-spots) 전용 bounding box 사전 필터. 정확한 반경 판정과 정렬은 호출부가
     * haversine으로 다시 계산한다({@code map_x is not null and map_y is not null}은 BETWEEN이
     * NULL을 자연히 배제하므로 별도 조건이 필요 없다).
     */
    @Query("""
            select place
            from TourPlace place
            where place.status = :status
              and place.mapX between :minLongitude and :maxLongitude
              and place.mapY between :minLatitude and :maxLatitude
            """)
    List<TourPlace> findAllVisibleWithinBoundingBox(
            @Param("status") TourPlaceStatus status,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TourPlace place
            set place.status = :inactiveStatus,
                place.updatedAt = :updatedAt
            where place.status = :activeStatus
              and place.contentTypeId = :contentTypeId
              and place.contentId not in :observedContentIds
            """)
    int markActiveMissingInScopeInactive(
            @Param("observedContentIds") Collection<String> observedContentIds,
            @Param("contentTypeId") String contentTypeId,
            @Param("activeStatus") TourPlaceStatus activeStatus,
            @Param("inactiveStatus") TourPlaceStatus inactiveStatus,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TourPlace place
            set place.status = :inactiveStatus,
                place.updatedAt = :updatedAt
            where place.status = :activeStatus
              and place.contentTypeId = :contentTypeId
            """)
    int markAllActiveInScopeInactive(
            @Param("contentTypeId") String contentTypeId,
            @Param("activeStatus") TourPlaceStatus activeStatus,
            @Param("inactiveStatus") TourPlaceStatus inactiveStatus,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
