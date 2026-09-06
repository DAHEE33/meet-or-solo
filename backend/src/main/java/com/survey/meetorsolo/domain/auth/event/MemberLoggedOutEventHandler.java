package com.survey.meetorsolo.domain.auth.event;

import com.survey.meetorsolo.global.config.WebSocketSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MemberLoggedOutEventHandler {

    private final WebSocketSessionRegistry sessions;

    public MemberLoggedOutEventHandler(WebSocketSessionRegistry sessions) {
        this.sessions = sessions;
    }

    // refresh token 폐기가 commit된 뒤에만 session을 끊어 rollback 시 연결이 사라지는 것을 막는다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoggedOut(MemberLoggedOutEvent event) {
        sessions.closeAll(event.memberId());
    }
}
