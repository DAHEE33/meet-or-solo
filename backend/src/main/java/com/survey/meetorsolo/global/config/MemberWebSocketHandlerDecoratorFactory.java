package com.survey.meetorsolo.global.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

@Component
public class MemberWebSocketHandlerDecoratorFactory implements WebSocketHandlerDecoratorFactory {

    private final WebSocketSessionRegistry sessions;

    public MemberWebSocketHandlerDecoratorFactory(WebSocketSessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                if (session.getPrincipal() instanceof WebSocketMemberPrincipal principal) {
                    sessions.register(principal.memberId(), session);
                }
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session,
                                              org.springframework.web.socket.CloseStatus closeStatus)
                    throws Exception {
                if (session.getPrincipal() instanceof WebSocketMemberPrincipal principal) {
                    sessions.remove(principal.memberId(), session);
                }
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }
}
