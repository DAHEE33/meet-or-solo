package com.survey.meetorsolo.domain.matching.event;

import com.survey.meetorsolo.domain.matching.dto.MatchingStateChangedNotification;
import com.survey.meetorsolo.domain.notification.policy.NotificationPolicy;
import com.survey.meetorsolo.domain.notification.service.NotificationAppendService;
import com.survey.meetorsolo.domain.notification.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchingStateChangedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MatchingStateChangedEventHandler.class);

    private static final String MATCHING_DESTINATION = "/queue/matching";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationAppendService notifications;
    private final PushNotificationService push;

    public MatchingStateChangedEventHandler(
            SimpMessagingTemplate messagingTemplate,
            NotificationAppendService notifications,
            PushNotificationService push) {
        this.messagingTemplate = messagingTemplate;
        this.notifications = notifications;
        this.push = push;
    }

    /**
     * 실시간 발송과 알림함 저장을 한자리에서 한다({@code docs/32} 3.3).
     *
     * <p><b>순서가 중요하다.</b> 먼저 WebSocket으로 보내고 그 다음에 저장한다. 저장이 느리거나
     * 실패해도 켜져 있는 화면은 제때 알림을 받아야 한다. 특히 {@code MATCH_PROPOSED}는 응답
     * 시간이 30초라 몇 백 ms도 손해다.
     *
     * <p>저장 실패를 삼키는 이유도 같다. 알림함은 부가 기능이고, 여기서 예외를 올리면 이미
     * 커밋된 매칭 결과와 무관한 자리에서 스택트레이스만 남는다. 대신 로그로 남겨 추적한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchingStateChangedEvent event) {
        MatchingStateChangedNotification notification =
                MatchingStateChangedNotification.of(event.reason(), event.occurredAt());
        event.memberIds().stream()
                .distinct()
                // 행위자 본인에게 관측자 시점 문구를 보내지 않는다(docs/31 5절 "알림 자기 반향").
                // 알림함·push에만 있던 규칙이라 "내가 도착을 눌렀는데 상대가 도착했다는 토스트가
                // 뜨고, 정작 알림함에는 없는" 상태가 됐다. 세 경로가 같은 판정을 쓴다.
                .filter(memberId -> NotificationPolicy.deliverableTo(
                        event.reason(), event.actorMemberId(), memberId))
                .forEach(memberId -> messagingTemplate.convertAndSendToUser(
                        String.valueOf(memberId),
                        MATCHING_DESTINATION,
                        notification
                ));

        try {
            notifications.append(
                    event.memberIds(), event.reason(), event.actorMemberId(), event.occurredAt());
        } catch (RuntimeException failure) {
            log.warn("알림함 저장에 실패했습니다. reason={}, occurredAt={}",
                    event.reason(), event.occurredAt(), failure);
        }

        // 앱이 꺼져 있는 기기까지 보낸다(docs/32 3.4). 실제 발송은 전용 thread로 넘어가므로
        // 여기서는 구독 조회 시간만 든다. 저장과 마찬가지로 실패를 삼킨다.
        try {
            push.notifyMembers(
                    event.memberIds(), event.reason(), event.actorMemberId(), event.occurredAt());
        } catch (RuntimeException failure) {
            log.warn("push 발송 준비에 실패했습니다. reason={}", event.reason(), failure);
        }
    }
}
