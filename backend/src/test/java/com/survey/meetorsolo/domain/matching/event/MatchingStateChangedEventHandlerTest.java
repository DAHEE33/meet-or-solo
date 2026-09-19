package com.survey.meetorsolo.domain.matching.event;

import static org.mockito.Mockito.never;
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
     * 내가 누른 일을 나에게 다시 알리지 않는다({@code docs/31} 5절 "알림 자기 반향").
     *
     * <p>예전에는 알림함·push에만 행위자 제외가 있고 WebSocket에는 없었다. 그래서 내가 도착을
     * 눌렀는데 "상대가 만남 장소에 도착했어요" 토스트가 나에게 떴고, 정작 알림함에는 그 줄이
     * 없었다 — 같은 이벤트인데 경로에 따라 결과가 달랐다.
     */
    @Test
    void 관측자시점알림은행위자에게보내지않는다() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        NotificationAppendService notifications =
                org.mockito.Mockito.mock(NotificationAppendService.class);
        PushNotificationService push = org.mockito.Mockito.mock(PushNotificationService.class);
        MatchingStateChangedEventHandler handler =
                new MatchingStateChangedEventHandler(messagingTemplate, notifications, push);
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-29T12:10:00+09:00");

        handler.handle(new MatchingStateChangedEvent(
                List.of(1L, 2L), "MEMBER_ARRIVED", occurredAt, 1L));

        MatchingStateChangedNotification notification =
                MatchingStateChangedNotification.of("MEMBER_ARRIVED", occurredAt);
        verify(messagingTemplate, times(1))
                .convertAndSendToUser("2", "/queue/matching", notification);
        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("1"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    /**
     * 그룹 전체의 사실은 행위자에게도 보낸다.
     *
     * <p>{@code MATCH_CONFIRMED}의 행위자는 <b>마지막으로 수락한 사람</b>이다. 행위자를 무조건
     * 걸러내면 매칭을 성사시킨 본인만 확정 배너를 못 본다.
     */
    @Test
    void 그룹사실은행위자에게도보낸다() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        NotificationAppendService notifications =
                org.mockito.Mockito.mock(NotificationAppendService.class);
        PushNotificationService push = org.mockito.Mockito.mock(PushNotificationService.class);
        MatchingStateChangedEventHandler handler =
                new MatchingStateChangedEventHandler(messagingTemplate, notifications, push);
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-29T12:00:20+09:00");

        handler.handle(new MatchingStateChangedEvent(
                List.of(1L, 2L), "MATCH_CONFIRMED", occurredAt, 1L));

        MatchingStateChangedNotification notification =
                MatchingStateChangedNotification.of("MATCH_CONFIRMED", occurredAt);
        verify(messagingTemplate, times(1))
                .convertAndSendToUser("1", "/queue/matching", notification);
        verify(messagingTemplate, times(1))
                .convertAndSendToUser("2", "/queue/matching", notification);
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
