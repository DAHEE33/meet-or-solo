package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPoint;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalMeetingPointRepository extends JpaRepository<FestivalMeetingPoint, Long> {
    List<FestivalMeetingPoint> findAllByFestivalIdOrderByAssignmentOrderAscIdAsc(long festivalId);
    List<FestivalMeetingPoint> findAllByFestivalIdAndStatusOrderByAssignmentOrderAscIdAsc(
            long festivalId, FestivalMeetingPointStatus status);
    boolean existsByFestivalIdAndStatus(long festivalId, FestivalMeetingPointStatus status);

    @Query(value = "SELECT * FROM festival_meeting_points WHERE id = :pointId FOR UPDATE", nativeQuery = true)
    Optional<FestivalMeetingPoint> findByIdForUpdate(@Param("pointId") long pointId);
}
