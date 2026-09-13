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

    /**
     * 마이페이지 체크인 기록 목록이다. 만료·취소된 체크인도 남긴다 — "기록"이므로 현재 유효한
     * 1건만 보는 {@link #findValidActiveCheckin}과 목적이 다르다.
     *
     * <p>cursor는 (체크인 시각, id) 복합이다. 같은 초에 들어온 행이 있어도 순서가 흔들리지
     * 않도록 id를 tiebreaker로 함께 비교한다.
     */
    @Query(value = """
            SELECT
                checkin.id AS checkinId,
                checkin.festival_id AS festivalId,
                checkin.distance_meters AS distanceMeters,
                checkin.status AS status,
                checkin.checked_in_at AS checkedInAt,
                checkin.expires_at AS expiresAt,
                festival.title AS festivalTitle,
                festival.address AS festivalAddress
            FROM festival_checkins checkin
            JOIN festivals festival ON festival.id = checkin.festival_id
            WHERE checkin.member_id = :memberId
              AND (
                  CAST(:cursorCheckedInAt AS timestamptz) IS NULL
                  OR (checkin.checked_in_at, checkin.id)
                     < (CAST(:cursorCheckedInAt AS timestamptz), :cursorCheckinId)
              )
            ORDER BY checkin.checked_in_at DESC, checkin.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<CheckinHistoryProjection> findHistoryByMemberId(
            @Param("memberId") long memberId,
            @Param("cursorCheckedInAt") OffsetDateTime cursorCheckedInAt,
            @Param("cursorCheckinId") long cursorCheckinId,
            @Param("limit") int limit
    );

    interface CheckinHistoryProjection {
        Long getCheckinId();
        Long getFestivalId();
        Integer getDistanceMeters();
        String getStatus();
        java.time.Instant getCheckedInAt();
        java.time.Instant getExpiresAt();
        String getFestivalTitle();
        String getFestivalAddress();
    }
}
