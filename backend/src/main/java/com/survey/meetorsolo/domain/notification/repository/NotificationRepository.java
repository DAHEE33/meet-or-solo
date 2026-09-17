package com.survey.meetorsolo.domain.notification.repository;

import com.survey.meetorsolo.domain.notification.entity.Notification;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByMemberIdOrderByCreatedAtDescIdDesc(long memberId, Limit limit);

    long countByMemberIdAndReadAtIsNull(long memberId);

    boolean existsByMemberIdAndReasonAndOccurredAt(
            long memberId, String reason, OffsetDateTime occurredAt);

    /**
     * 목록을 열면 전부 읽음으로 본다({@code docs/32} 5절 6번).
     *
     * <p>개별 읽음을 두지 않은 이유는 뱃지 때문이다. 목록을 펼쳤는데 숫자가 남아 있으면
     * 무엇이 새 알림인지 구분할 방법이 없다. 1단계 프론트 동작을 그대로 옮겼다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.memberId = :memberId and n.readAt is null")
    int markAllRead(@Param("memberId") long memberId, @Param("now") OffsetDateTime now);

    /** 보관 기간이 지난 알림을 지운다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.memberId = :memberId and n.createdAt < :threshold")
    int deleteOlderThan(
            @Param("memberId") long memberId, @Param("threshold") OffsetDateTime threshold);

    /**
     * 보관 건수를 넘은 오래된 알림을 지운다.
     *
     * <p>JPQL에는 LIMIT이 없어 native query로 둔다. 하위 조회가 같은 테이블을 보므로
     * PostgreSQL 기준으로 작성했다(이 프로젝트는 모든 환경이 PostgreSQL이다).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM notifications
            WHERE member_id = :memberId
              AND id NOT IN (
                  SELECT id FROM notifications
                  WHERE member_id = :memberId
                  ORDER BY created_at DESC, id DESC
                  LIMIT :keep
              )
            """, nativeQuery = true)
    int deleteBeyondNewest(@Param("memberId") long memberId, @Param("keep") int keep);
}
