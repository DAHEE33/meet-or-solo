package com.survey.meetorsolo.domain.matching.event;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.survey.meetorsolo.domain.matching.dto.MatchingStateChangedNotification;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class MatchingStateChangedEventHandlerTest {

    @Test
    void 회원별Queue에중복없이상태변경알림을전송한다() {
        SimpMessagingTemplate messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        MatchingStateChangedEventHandler handler = new MatchingStateChangedEventHandler(messagingTemplate);
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
        MatchingStateChangedEventHandler handler =
                new MatchingStateChangedEventHandler(messagingTemplate);
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
}
