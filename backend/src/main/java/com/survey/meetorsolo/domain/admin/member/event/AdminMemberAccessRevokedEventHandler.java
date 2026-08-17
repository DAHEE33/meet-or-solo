package com.survey.meetorsolo.domain.admin.member.event;

import com.survey.meetorsolo.global.config.WebSocketSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AdminMemberAccessRevokedEventHandler {

    private final WebSocketSessionRegistry sessions;

    public AdminMemberAccessRevokedEventHandler(WebSocketSessionRegistry sessions) {
        this.sessions = sessions;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessRevoked(AdminMemberAccessRevokedEvent event) {
        sessions.closeAll(event.memberId());
    }
}
