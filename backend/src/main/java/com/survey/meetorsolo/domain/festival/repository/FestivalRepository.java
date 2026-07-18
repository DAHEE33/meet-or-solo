package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
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

    @Query("""
            select festival
            from Festival festival
            where festival.status = :status
              and (festival.eventEndDate is null or festival.eventEndDate >= :today)
            """)
    Page<Festival> findVisibleFestivals(
            @Param("status") FestivalStatus status,
            @Param("today") LocalDate today,
            Pageable pageable
    );
}
