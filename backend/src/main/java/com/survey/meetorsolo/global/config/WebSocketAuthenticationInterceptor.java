package com.survey.meetorsolo.global.config;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class WebSocketAuthenticationInterceptor implements HandshakeInterceptor {

    static final String MEMBER_ID_ATTRIBUTE = "matchingWebSocketMemberId";
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final MemberAccessPolicy accessPolicy;

    public WebSocketAuthenticationInterceptor(JwtProvider jwtProvider, MemberAccessPolicy accessPolicy) {
        this.jwtProvider = jwtProvider;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        String accessToken = accessToken(servletRequest.getServletRequest());
        if (accessToken == null || accessToken.isBlank()) {
            return false;
        }
        long memberId = jwtProvider.getMemberIdFromAccessToken(accessToken);
        accessPolicy.requireAccessible(memberId);
        attributes.put(MEMBER_ID_ATTRIBUTE, memberId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private String accessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
