package com.survey.meetorsolo.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 브라우저 Web Push 구독({@code docs/32} 3.4).
 *
 * <p>구독의 신원은 {@code endpoint}다. 같은 브라우저가 다시 구독하면 같은 endpoint가 오므로
 * 새 행을 만들지 않고 주인을 바꾼다 — 한 기기에서 로그아웃하고 다른 계정으로 들어왔을 때
 * 예전 주인에게 알림이 가면 안 된다.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 255)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PushSubscription() {
    }

    public static PushSubscription of(
            long memberId, String endpoint, String p256dh, String auth, OffsetDateTime now) {
        PushSubscription subscription = new PushSubscription();
        subscription.memberId = memberId;
        subscription.endpoint = endpoint;
        subscription.p256dh = p256dh;
        subscription.auth = auth;
        subscription.createdAt = now;
        subscription.updatedAt = now;
        return subscription;
    }

    /** 같은 endpoint를 다른 회원이 다시 등록했을 때 주인과 키를 옮긴다. */
    public void reassign(long memberId, String p256dh, String auth, OffsetDateTime now) {
        this.memberId = memberId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }
}
