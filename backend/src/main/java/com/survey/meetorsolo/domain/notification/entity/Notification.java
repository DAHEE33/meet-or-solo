package com.survey.meetorsolo.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 회원 알림함의 한 줄({@code docs/32} 3.3).
 *
 * <p>문구를 담지 않고 {@code reason}만 담는다. WebSocket 1단계와 같은 구조다 — 문구와 이동
 * 경로는 프론트(`notificationMessages.ts`)가 정한다. 문구를 서버가 저장하면 문구를 고칠 때마다
 * 과거 알림이 옛 문장으로 남는다.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "reason", nullable = false, length = 40)
    private String reason;

    @Column(name = "actor_member_id")
    private Long actorMemberId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Notification() {
    }

    public static Notification of(
            long memberId, String reason, Long actorMemberId,
            OffsetDateTime occurredAt, OffsetDateTime now) {
        Notification notification = new Notification();
        notification.memberId = memberId;
        notification.reason = reason;
        notification.actorMemberId = actorMemberId;
        notification.occurredAt = occurredAt;
        notification.createdAt = now;
        return notification;
    }

    /** 이미 읽은 알림이면 아무것도 하지 않는다. 읽은 시각을 뒤로 밀지 않기 위해서다. */
    public boolean markRead(OffsetDateTime now) {
        if (readAt != null) return false;
        readAt = now;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getReason() {
        return reason;
    }

    public Long getActorMemberId() {
        return actorMemberId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
