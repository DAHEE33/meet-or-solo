package com.survey.meetorsolo.domain.tourplace.repository;

import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.global.region.RegionAggregate;
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
     * 목록/검색 화면 전용. 좋아요(찜)·후기(댓글) 수를 함께 집계하고 그 값으로 정렬까지 하므로
     * native query다 — 집계 값은 엔티티 속성이 아니라서 {@code Pageable}의 {@code Sort}로
     * 표현할 수 없다. 축제 쪽 {@code findVisibleFestivals}와 같은 구조이며, 근거도 같다.
     *
     * <p>{@code raw_data} JSONB와 좌표처럼 목록에 쓰이지 않는 컬럼은 여전히 읽지 않는다.
     */
    @Query(value = """
            SELECT * FROM (
                SELECT
                    place.id AS id,
                    place.content_id AS contentId,
                    place.content_type_id AS contentTypeId,
                    place.title AS title,
                    place.address AS address,
                    place.status AS status,
                    place.image_url AS imageUrl,
                    place.created_at AS createdAt,
                    (SELECT COUNT(*) FROM content_bookmarks bookmark
                      WHERE bookmark.tour_place_id = place.id) AS bookmarkCount,
                    (SELECT COUNT(*) FROM content_comments comment
                      WHERE comment.tour_place_id = place.id
                        AND comment.status = 'VISIBLE') AS commentCount
                FROM tour_places place
                WHERE place.status = :status
                  AND (CAST(:contentTypeId AS varchar) IS NULL
                       OR place.content_type_id = :contentTypeId)
                  AND (CAST(:sigunguCode AS varchar) IS NULL
                       OR place.sigungu_code = :sigunguCode)
                  AND lower(place.title) LIKE lower(concat('%', :keyword, '%'))
            ) ranked
            ORDER BY
                CASE WHEN :sort = 'BOOKMARK_COUNT_DESC' THEN ranked.bookmarkCount
                     WHEN :sort = 'COMMENT_COUNT_DESC' THEN ranked.commentCount
                     ELSE 0 END DESC,
                CASE WHEN :sort = 'RECENTLY_ADDED' THEN ranked.createdAt END DESC,
                CASE WHEN :sort = 'TITLE_ASC' THEN ranked.title END ASC,
                ranked.id ASC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM tour_places place
            WHERE place.status = :status
              AND (CAST(:contentTypeId AS varchar) IS NULL
                   OR place.content_type_id = :contentTypeId)
              AND (CAST(:sigunguCode AS varchar) IS NULL
                   OR place.sigungu_code = :sigunguCode)
              AND lower(place.title) LIKE lower(concat('%', :keyword, '%'))
            """,
            nativeQuery = true)
    Page<TourPlaceListProjection> findVisiblePlaces(
            @Param("status") String status,
            @Param("contentTypeId") String contentTypeId,
            @Param("sigunguCode") String sigunguCode,
            @Param("keyword") String keyword,
            @Param("sort") String sort,
            Pageable pageable
    );

    /**
     * {@link #findVisiblePlaces}의 결과 1행. native query라 생성자 표현식을 쓸 수 없어
     * 인터페이스 프로젝션이다.
     */
    interface TourPlaceListProjection {
        Long getId();

        String getContentId();

        String getContentTypeId();

        String getTitle();

        String getAddress();

        /** {@code TourPlaceStatus} 이름. native query라 enum으로 직접 받지 않는다. */
        String getStatus();

        String getImageUrl();

        /** 이 관광지를 찜한 회원 수. 화면에는 "좋아요 수"로 표시한다. */
        long getBookmarkCount();

        /** 공개({@code VISIBLE}) 댓글 수. */
        long getCommentCount();
    }

    /**
     * 지역 선택 UI용 시군구 집계. 축제 쪽 {@code aggregateVisibleRegions}와 같은 방식으로
     * 대표 주소를 함께 가져와 이름을 뽑는다(시군구 이름이 DB에 없다).
     */
    @Query("""
            select new com.survey.meetorsolo.global.region.RegionAggregate(
                place.sigunguCode, min(place.address), count(place.id))
            from TourPlace place
            where place.status = :status
              and (:contentTypeId is null or place.contentTypeId = :contentTypeId)
              and place.sigunguCode is not null
              and place.address is not null
            group by place.sigunguCode
            """)
    List<RegionAggregate> aggregateVisibleRegions(
            @Param("status") TourPlaceStatus status,
            @Param("contentTypeId") String contentTypeId
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
