package com.survey.meetorsolo.domain.festival.repository;

import com.survey.meetorsolo.domain.festival.entity.FestivalCheckin;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalCheckinRepository extends JpaRepository<FestivalCheckin, Long> {

    List<FestivalCheckin> findAllByMemberIdAndStatus(Long memberId, FestivalCheckinStatus status);

    @Query(value = """
            SELECT * FROM festival_checkins
            WHERE member_id = :memberId
              AND status = 'ACTIVE'
              AND expires_at > :now
            ORDER BY checked_in_at DESC, id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<FestivalCheckin> findValidActiveCheckin(
            @Param("memberId") Long memberId,
            @Param("now") OffsetDateTime now
    );
}
