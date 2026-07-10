package com.survey.meetorsolo.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.service.AuthService;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController controller = new AuthController(authService, "http://localhost:5173", false);

    @Test
    void 네이버_로그인은_state_쿠키와_authorize_url을_생성한다() {
        when(authService.getNaverAuthorizeUri(anyString())).thenAnswer(invocation ->
                URI.create("https://nid.naver.com/oauth2.0/authorize?state=" + invocation.getArgument(0)));

        ResponseEntity<Void> response = controller.naverLogin();

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).startsWith("https://nid.naver.com/");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("oauth_state_naver=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=300");
    }

    @Test
    void 네이버_callback은_state가_누락되면_실패한다() {
        ResponseEntity<Void> response = controller.naverCallback("code", null, null, "expected");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("http://localhost:5173/login?oauthError=invalid_callback");
        verifyNoInteractions(authService);
    }

    @Test
    void 네이버_callback은_state가_불일치하면_실패한다() {
        ResponseEntity<Void> response = controller.naverCallback("code", "actual", null, "expected");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).contains("invalid_callback");
        verifyNoInteractions(authService);
    }

    @Test
    void PROFILE_REQUIRED_네이버_회원은_signup으로_이동한다() {
        when(authService.loginWithNaver("code", "state")).thenReturn(new AuthTokenResponse(
                "Bearer", "access", "refresh", 1800, 1209600, 1L, "PROFILE_REQUIRED"));

        ResponseEntity<Void> response = controller.naverCallback("code", "state", null, "state");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("http://localhost:5173/signup");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(3);
    }

    @Test
    void ACTIVE_네이버_회원은_홈으로_이동한다() {
        when(authService.loginWithNaver("code", "state")).thenReturn(new AuthTokenResponse(
                "Bearer", "access", "refresh", 1800, 1209600, 1L, "ACTIVE"));

        ResponseEntity<Void> response = controller.naverCallback("code", "state", null, "state");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("http://localhost:5173/");
    }
}
