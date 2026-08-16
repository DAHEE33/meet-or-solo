package com.survey.meetorsolo.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

class WebSocketAuthenticationInterceptorTest {

    @Test
    void accessTokenCookie를검증해회원Id를Handshake속성에저장한다() {
        JwtProvider jwtProvider = mock(JwtProvider.class);
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(7L);
        WebSocketAuthenticationInterceptor interceptor =
                new WebSocketAuthenticationInterceptor(jwtProvider);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("access_token", "valid-token"));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketAuthenticationInterceptor.MEMBER_ID_ATTRIBUTE, 7L);
    }

    @Test
    void accessTokenCookie가없으면Handshake를거절한다() {
        WebSocketAuthenticationInterceptor interceptor =
                new WebSocketAuthenticationInterceptor(mock(JwtProvider.class));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
    }
}
