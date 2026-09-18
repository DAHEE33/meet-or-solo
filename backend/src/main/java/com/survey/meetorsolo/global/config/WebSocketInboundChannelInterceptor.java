package com.survey.meetorsolo.global.config;

import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class WebSocketInboundChannelInterceptor implements ChannelInterceptor {

    private static final String MATCHING_USER_DESTINATION = "/user/queue/matching";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }
        Principal user = accessor.getUser();
        if (StompCommand.CONNECT.equals(command) && user == null) {
            throw new AccessDeniedException("인증되지 않은 WebSocket 연결입니다.");
        }
        if (StompCommand.SEND.equals(command)) {
            throw new AccessDeniedException("client STOMP SEND는 허용하지 않습니다.");
        }
        if (StompCommand.SUBSCRIBE.equals(command)
                && !MATCHING_USER_DESTINATION.equals(accessor.getDestination())) {
            throw new AccessDeniedException("허용되지 않은 STOMP 구독 경로입니다.");
        }
        return message;
    }
}
