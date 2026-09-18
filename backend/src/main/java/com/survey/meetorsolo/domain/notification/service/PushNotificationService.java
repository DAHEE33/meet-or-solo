package com.survey.meetorsolo.domain.notification.service;

import com.survey.meetorsolo.domain.notification.entity.PushSubscription;
import com.survey.meetorsolo.domain.notification.policy.NotificationPolicy;
import com.survey.meetorsolo.domain.notification.repository.PushSubscriptionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태 변화를 앱이 꺼져 있는 기기까지 보낸다({@code docs/32} 3.4).
 *
 * <p>내용을 서버가 문장으로 만들지 않고 {@code reason}만 실어 보낸다. WebSocket·알림함과 같은
 * 구조다 — 문구와 이동 경로는 service worker가 프론트의 매핑(`notificationMessages.ts`)으로
 * 정한다. 서버가 문장을 담으면 문구를 고칠 때 이미 보낸 알림과 화면이 갈린다.
 */
@Service
public class PushNotificationService {

    private final PushSubscriptionRepository subscriptions;
    private final WebPushSender sender;
    private final PushSubscriptionCleanupService cleanup;

    public PushNotificationService(
            PushSubscriptionRepository subscriptions,
            WebPushSender sender,
            PushSubscriptionCleanupService cleanup) {
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.cleanup = cleanup;
    }

    /**
     * 수신자들에게 push를 보낸다.
     *
     * <p>행위자 본인은 건너뛴다. 알림함과 같은 규칙이다 — 내가 누른 일을 내 잠금 화면이
     * 다시 알려 줄 이유가 없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyMembers(
            List<Long> memberIds, String reason, Long actorMemberId, OffsetDateTime occurredAt) {
        if (!sender.enabled() || !NotificationPolicy.pushed(reason)) return;

        String payload = payload(reason, occurredAt);
        memberIds.stream()
                .distinct()
                .filter(memberId -> actorMemberId == null || actorMemberId.longValue() != memberId)
                .flatMap(memberId -> subscriptions.findAllByMemberId(memberId).stream())
                .forEach(subscription -> send(subscription, payload));
    }

    private void send(PushSubscription subscription, String payload) {
        String endpoint = subscription.getEndpoint();
        sender.sendAsync(
                endpoint,
                subscription.getP256dh(),
                subscription.getAuth(),
                payload,
                () -> cleanup.removeGone(endpoint));
    }

    /** WebSocket이 보내는 것과 같은 모양이다. service worker가 같은 매핑을 쓸 수 있다. */
    private static String payload(String reason, OffsetDateTime occurredAt) {
        return """
                {"type":"MATCHING_STATE_CHANGED","reason":"%s","occurredAt":"%s"}"""
                .formatted(reason, occurredAt);
    }
}
