package com.survey.meetorsolo.global.config;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(long memberId, WebSocketSession session) {
        sessions.computeIfAbsent(memberId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(long memberId, WebSocketSession session) {
        Set<WebSocketSession> memberSessions = sessions.get(memberId);
        if (memberSessions == null) return;
        memberSessions.remove(session);
        if (memberSessions.isEmpty()) sessions.remove(memberId, memberSessions);
    }

    public void closeAll(long memberId) {
        Set<WebSocketSession> memberSessions = sessions.remove(memberId);
        if (memberSessions == null) return;
        for (WebSocketSession session : memberSessions) {
            try {
                if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {
                // 이미 종료 중인 session은 멱등하게 무시합니다.
            }
        }
    }
}
