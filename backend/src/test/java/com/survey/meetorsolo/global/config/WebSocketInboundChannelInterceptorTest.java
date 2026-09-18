package com.survey.meetorsolo.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

class WebSocketInboundChannelInterceptorTest {

    private final WebSocketInboundChannelInterceptor interceptor =
            new WebSocketInboundChannelInterceptor();

    @Test
    void 인증회원의본인MatchingQueue구독만허용한다() {
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/user/queue/matching", true);

        assertThatCode(() -> interceptor.preSend(message, ignoredChannel()))
                .doesNotThrowAnyException();
    }

    @Test
    void 다른구독경로와ClientSend를거절한다() {
        Message<byte[]> otherSubscription = message(
                StompCommand.SUBSCRIBE,
                "/topic/match-attempts/1",
                true
        );
        Message<byte[]> send = message(StompCommand.SEND, "/app/chat", true);

        assertThatThrownBy(() -> interceptor.preSend(otherSubscription, ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(send, ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 인증되지않은Connect를거절한다() {
        Message<byte[]> message = message(StompCommand.CONNECT, null, false);

        assertThatThrownBy(() -> interceptor.preSend(message, ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> message(StompCommand command, String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("test-session");
        if (destination != null) {
            accessor.setDestination(destination);
            accessor.setSubscriptionId("subscription-1");
        }
        if (authenticated) {
            accessor.setUser(new WebSocketMemberPrincipal(1L));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private MessageChannel ignoredChannel() {
        return org.mockito.Mockito.mock(MessageChannel.class);
    }
}
