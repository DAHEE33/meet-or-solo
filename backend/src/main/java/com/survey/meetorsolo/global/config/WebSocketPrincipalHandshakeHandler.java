package com.survey.meetorsolo.global.config;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class WebSocketPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        Object memberId = attributes.get(WebSocketAuthenticationInterceptor.MEMBER_ID_ATTRIBUTE);
        if (!(memberId instanceof Long value)) {
            return null;
        }
        return new WebSocketMemberPrincipal(value);
    }
}
