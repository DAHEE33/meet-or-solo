package com.survey.meetorsolo.domain.auth.service;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * session cookie 발급 규칙 한 곳.
 *
 * <p>OAuth 콜백과 관리자 ID/PW 로그인({@code docs/30})이 같은 cookie를 발급하고
 * {@code /api/auth/logout} 하나가 둘 다 지운다. 속성이 한 곳에만 있어야 하는 이유가
 * 이것이다 — 발급과 삭제의 속성이 어긋나면 브라우저가 cookie를 지우지 않아 로그아웃이
 * 조용히 실패한다.
 */
public final class AuthCookieFactory {

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";

    private final boolean secure;

    public AuthCookieFactory(boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie token(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie expired(String name) {
        return token(name, "", Duration.ZERO);
    }
}
