package com.survey.meetorsolo.domain.matching.event;

import com.survey.meetorsolo.domain.matching.dto.MatchingStateChangedNotification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchingStateChangedEventHandler {

    private static final String MATCHING_DESTINATION = "/queue/matching";

    private final SimpMessagingTemplate messagingTemplate;

    public MatchingStateChangedEventHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchingStateChangedEvent event) {
        MatchingStateChangedNotification notification =
                MatchingStateChangedNotification.of(event.reason(), event.occurredAt());
        event.memberIds().stream()
                .distinct()
                .forEach(memberId -> messagingTemplate.convertAndSendToUser(
                        String.valueOf(memberId),
                        MATCHING_DESTINATION,
                        notification
                ));
    }
}
