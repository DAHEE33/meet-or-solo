package com.survey.meetorsolo.domain.auth.controller;

import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.service.AuthService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String OAUTH_STATE_COOKIE = "oauth_state";
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(5);

    private final AuthService authService;
    private final String frontendBaseUrl;
    private final boolean secureCookies;

    public AuthController(
            AuthService authService,
            @Value("${app.frontend.base-url}") String frontendBaseUrl,
            @Value("${app.auth.cookie-secure}") boolean secureCookies
    ) {
        this.authService = authService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.secureCookies = secureCookies;
    }

    @GetMapping("/api/auth/kakao/login")
    public ResponseEntity<Void> kakaoLogin() {
        String state = UUID.randomUUID().toString();
        URI authorizeUri = authService.getKakaoAuthorizeUri(state);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizeUri.toString())
                .header(HttpHeaders.SET_COOKIE, oauthStateCookie(state).toString())
                .build();
    }

    @GetMapping("/api/auth/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(name = OAUTH_STATE_COOKIE, required = false) String expectedState
    ) {
        if (error != null || code == null || code.isBlank() || !matchesState(expectedState, state)) {
            return redirectFailure("invalid_callback");
        }

        try {
            AuthTokenResponse tokenResponse = authService.loginWithKakao(code);
            String destination = "PROFILE_REQUIRED".equals(tokenResponse.memberStatus()) ? "/signup" : "/";
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, frontendBaseUrl + destination)
                    .header(HttpHeaders.SET_COOKIE, tokenCookie(
                            ACCESS_TOKEN_COOKIE,
                            tokenResponse.accessToken(),
                            Duration.ofSeconds(tokenResponse.accessTokenExpiresInSeconds())
                    ).toString())
                    .header(HttpHeaders.SET_COOKIE, tokenCookie(
                            REFRESH_TOKEN_COOKIE,
                            tokenResponse.refreshToken(),
                            Duration.ofSeconds(tokenResponse.refreshTokenExpiresInSeconds())
                    ).toString())
                    .header(HttpHeaders.SET_COOKIE, clearOauthStateCookie().toString())
                    .build();
        } catch (RuntimeException exception) {
            log.warn("Kakao OAuth callback failed: {}", exception.getClass().getSimpleName());
            return redirectFailure("oauth_failed");
        }
    }

    private boolean matchesState(String expectedState, String actualState) {
        if (expectedState == null || actualState == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedState.getBytes(StandardCharsets.UTF_8),
                actualState.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ResponseEntity<Void> redirectFailure(String reason) {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendBaseUrl + "/login?oauthError=" + reason)
                .header(HttpHeaders.SET_COOKIE, clearOauthStateCookie().toString())
                .build();
    }

    private ResponseCookie oauthStateCookie(String state) {
        return ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/api/auth/kakao/callback")
                .maxAge(OAUTH_STATE_TTL)
                .build();
    }

    private ResponseCookie clearOauthStateCookie() {
        return ResponseCookie.from(OAUTH_STATE_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/api/auth/kakao/callback")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie tokenCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
