package com.survey.meetorsolo.domain.matching.event;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.survey.meetorsolo.domain.matching.dto.MatchingStateChangedNotification;
import com.survey.meetorsolo.domain.notification.service.NotificationAppendService;
import com.survey.meetorsolo.domain.notification.service.PushNotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class MatchingStateChangedEventHandlerTest {

    @Test
    void 회원별Queue에중복없이상태변경알림을전송한다() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        NotificationAppendService notifications =
                org.mockito.Mockito.mock(NotificationAppendService.class);
        PushNotificationService push = org.mockito.Mockito.mock(PushNotificationService.class);
        MatchingStateChangedEventHandler handler =
                new MatchingStateChangedEventHandler(messagingTemplate, notifications, push);
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-29T12:00:00+09:00");

        handler.handle(new MatchingStateChangedEvent(
                List.of(1L, 2L, 1L),
                "MATCH_PROPOSED",
                occurredAt
        ));

        MatchingStateChangedNotification notification =
                MatchingStateChangedNotification.of("MATCH_PROPOSED", occurredAt);
        verify(messagingTemplate, times(1))
                .convertAndSendToUser("1", "/queue/matching", notification);
        verify(messagingTemplate, times(1))
                .convertAndSendToUser("2", "/queue/matching", notification);
    }

    @Test
    void 도착시간변경은기존회원Queue에refresh알림만전송한다() {
        SimpMessagingTemplate messagingTemplate =
                org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        NotificationAppendService notifications =
                org.mockito.Mockito.mock(NotificationAppendService.class);
        PushNotificationService push = org.mockito.Mockito.mock(PushNotificationService.class);
        MatchingStateChangedEventHandler handler =
                new MatchingStateChangedEventHandler(messagingTemplate, notifications, push);
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-29T12:05:00+09:00");

        handler.handle(new MatchingStateChangedEvent(
                List.of(1L, 2L),
                "ARRIVAL_TIME_SELECTED",
                occurredAt
        ));

        MatchingStateChangedNotification notification =
                MatchingStateChangedNotification.of("ARRIVAL_TIME_SELECTED", occurredAt);
        verify(messagingTemplate).convertAndSendToUser(
                "1",
                "/queue/matching",
                notification
        );
        verify(messagingTemplate).convertAndSendToUser(
                "2",
                "/queue/matching",
                notification
        );
    }

    /** 알림함 저장은 실시간 발송과 같은 자리에서 위임한다(docs/32 3.3). */
    @Test
    void 상태변경을알림함에도넘긴다() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        NotificationAppendService notifications =
                org.mockito.Mockito.mock(NotificationAppendService.class);
        PushNotificationService push = org.mockito.Mockito.mock(PushNotificationService.class);
        MatchingStateChangedEventHandler handler =
                new MatchingStateChangedEventHandler(messagingTemplate, notifications, push);
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-29T12:00:00+09:00");

        handler.handle(new MatchingStateChangedEvent(
                List.of(1L, 2L), "MATCH_PROPOSED", occurredAt, 2L));

        verify(notifications).append(List.of(1L, 2L), "MATCH_PROPOSED", 2L, occurredAt);
    }

    /**
     * 저장 실패가 실시간 알림을 막지 않는다. 알림함은 부가 기능이고, 여기서 예외를 올리면
     * 이미 커밋된 매칭 결과와 무관한 자리에서 터진다.
     */
    @Test
    void 알림함저장이실패해도실시간발송은유지된다() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        NotificationAppendService notifications =
                org.mockito.Mockito.mock(NotificationAppendService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(notifications).append(
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        PushNotificationService push = org.mockito.Mockito.mock(PushNotificationService.class);
        MatchingStateChangedEventHandler handler =
                new MatchingStateChangedEventHandler(messagingTemplate, notifications, push);
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-29T12:00:00+09:00");

        handler.handle(new MatchingStateChangedEvent(List.of(1L), "MATCH_CONFIRMED", occurredAt));

        verify(messagingTemplate).convertAndSendToUser(
                "1",
                "/queue/matching",
                MatchingStateChangedNotification.of("MATCH_CONFIRMED", occurredAt));
    }
}
