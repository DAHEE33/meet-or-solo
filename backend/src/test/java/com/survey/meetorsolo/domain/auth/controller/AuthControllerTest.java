package com.survey.meetorsolo.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.service.AuthService;
import com.survey.meetorsolo.domain.auth.service.SanctionNoticeCookieService;
import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberSanctionReason;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.domain.member.service.MemberSanctionException;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class AuthControllerTest {

    private static final OffsetDateTime SUSPENDED_UNTIL = OffsetDateTime.parse("2026-09-15T10:00:00+09:00");

    private final AuthService authService = mock(AuthService.class);
    private final MemberAccessPolicy memberAccessPolicy = mock(MemberAccessPolicy.class);
    private final JwtProvider jwtProvider = new JwtProvider(
            new ObjectMapper(), "test-jwt-secret-that-is-long-enough", 30, 20160);
    private final SanctionNoticeCookieService sanctionNoticeCookies =
            new SanctionNoticeCookieService(jwtProvider, false);
    private final AuthController controller = new AuthController(
            authService, memberAccessPolicy, sanctionNoticeCookies, "http://localhost:5173", false);

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

    @Test
    void 제재_회원의_로그인은_재시도_안내가_아니라_제재_안내로_보낸다() {
        when(authService.loginWithNaver("code", "state")).thenThrow(sanctionException());

        ResponseEntity<Void> response = controller.naverCallback("code", "state", null, "state");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        // oauth_failed로 보내면 로그인 화면이 "잠시 후 다시 시도해 주세요"라는 틀린 안내를 띄운다.
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("http://localhost:5173/login?oauthError=account_restricted");
    }

    @Test
    void 제재_회원의_로그인은_안내_조회용_cookie를_발급한다() {
        when(authService.loginWithNaver("code", "state")).thenThrow(sanctionException());

        List<String> cookies = controller.naverCallback("code", "state", null, "state")
                .getHeaders().get(HttpHeaders.SET_COOKIE);

        String notice = cookies.stream()
                .filter(cookie -> cookie.startsWith("sanction_notice="))
                .findFirst()
                .orElseThrow();
        assertThat(notice)
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/api/auth/sanction-notice")
                .contains("Max-Age=300");
    }

    @Test
    void 제재_사유는_redirect_URL에_담지_않는다() {
        when(authService.loginWithNaver("code", "state")).thenThrow(sanctionException());

        ResponseEntity<Void> response = controller.naverCallback("code", "state", null, "state");

        // URL에 담으면 nginx access log와 브라우저 history에 제재 정보가 남는다.
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location)
                .doesNotContain("HARASSMENT")
                .doesNotContain("suspendedUntil")
                .doesNotContain("2026-09-15");
    }

    @Test
    void notice_cookie가_없으면_안내를_주지_않는다() {
        ResponseEntity<ApiResponse<MemberSanctionNotice>> response = controller.sanctionNotice(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
        verifyNoInteractions(memberAccessPolicy);
    }

    @Test
    void notice_cookie로_사유와_기간을_조회한다() {
        when(memberAccessPolicy.findSanctionNotice(7L)).thenReturn(notice());

        ResponseEntity<ApiResponse<MemberSanctionNotice>> response =
                controller.sanctionNotice(jwtProvider.createSanctionNoticeToken(7L));

        MemberSanctionNotice body = response.getBody().data();
        assertThat(body.status()).isEqualTo(Member.STATUS_SUSPENDED);
        assertThat(body.suspendedUntil()).isEqualTo(SUSPENDED_UNTIL);
        assertThat(body.reasonCode()).isEqualTo("HARASSMENT");
    }

    @Test
    void 안내를_읽은_뒤에는_notice_cookie를_만료시킨다() {
        when(memberAccessPolicy.findSanctionNotice(7L)).thenReturn(notice());

        ResponseEntity<ApiResponse<MemberSanctionNotice>> response =
                controller.sanctionNotice(jwtProvider.createSanctionNoticeToken(7L));

        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("sanction_notice=;")
                .contains("Max-Age=0");
    }

    @Test
    void accessToken으로는_안내를_조회할_수_없다() {
        assertThatThrownBy(() -> controller.sanctionNotice(jwtProvider.createAccessToken(7L, "SUSPENDED")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(memberAccessPolicy);
    }

    private static MemberSanctionException sanctionException() {
        return new MemberSanctionException(ErrorCode.MEMBER_SUSPENDED, 7L, notice());
    }

    private static MemberSanctionNotice notice() {
        return new MemberSanctionNotice(
                Member.STATUS_SUSPENDED,
                SUSPENDED_UNTIL,
                MemberSanctionReason.HARASSMENT.name(),
                MemberSanctionReason.HARASSMENT.getUserMessage(),
                null);
    }
}
