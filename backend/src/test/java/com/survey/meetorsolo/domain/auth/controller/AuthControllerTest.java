package com.survey.meetorsolo.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.service.AuthService;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
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

    @Test
    void 로그아웃은_access와_refresh_cookie를_발급과_동일한_속성으로_만료시킨다() {
        when(authService.loginWithNaver("code", "state")).thenReturn(new AuthTokenResponse(
                "Bearer", "access", "refresh", 1800, 1209600, 1L, "ACTIVE"));
        List<String> issued = controller.naverCallback("code", "state", null, "state")
                .getHeaders().get(HttpHeaders.SET_COOKIE);

        ResponseEntity<Void> response = controller.logout("access-token");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(authService).logout("access-token");
        List<String> cleared = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cleared).hasSize(2);
        // 속성이 발급 때와 하나라도 다르면 브라우저가 cookie를 지우지 않는다.
        assertThat(attributes(cleared.get(0))).isEqualTo(attributes(issued.get(0)));
        assertThat(attributes(cleared.get(1))).isEqualTo(attributes(issued.get(1)));
        assertThat(cleared.get(0)).startsWith("access_token=;").contains("Max-Age=0");
        assertThat(cleared.get(1)).startsWith("refresh_token=;").contains("Max-Age=0");
    }

    /** 값과 수명을 뺀 cookie 속성만 남긴다. Path, HttpOnly, Secure, SameSite 일치를 비교하기 위한 것이다. */
    private static List<String> attributes(String setCookie) {
        return Arrays.stream(setCookie.split("; "))
                .skip(1)
                .filter(attribute -> !attribute.startsWith("Max-Age=") && !attribute.startsWith("Expires="))
                .toList();
    }

    @Test
    void 토큰이_없어도_로그아웃은_204와_cookie_만료를_돌려준다() {
        ResponseEntity<Void> response = controller.logout(null);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(authService).logout(null);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2);
    }
}
