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

    /**
     * 이 회원이 이 축제에 체크인한 적이 있는가. 축제 댓글 작성 자격 판정에 쓴다
     * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 5.2).
     *
     * <p><b>상태를 보지 않는다.</b> 취소({@code CANCELLED})와 만료도 "간 적 있음"으로 센다 —
     * 취소는 매칭풀에서 빠지려는 행위이지 방문을 부정하는 것이 아니고, 체크인 유효기간이
     * 1시간이라 상태를 보면 현장을 떠난 순간 후기를 남길 수 없게 된다.
     */
    boolean existsByMemberIdAndFestivalId(Long memberId, Long festivalId);

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
