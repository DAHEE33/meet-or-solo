package com.survey.meetorsolo.domain.tourplace.repository;

import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
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

    @Query("""
            select place
            from TourPlace place
            where place.status = :status
              and (:contentTypeId is null or place.contentTypeId = :contentTypeId)
            """)
    Page<TourPlace> findVisiblePlaces(
            @Param("status") TourPlaceStatus status,
            @Param("contentTypeId") String contentTypeId,
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
