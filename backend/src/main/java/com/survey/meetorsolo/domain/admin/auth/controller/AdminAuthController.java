package com.survey.meetorsolo.domain.admin.auth.controller;

import com.survey.meetorsolo.domain.admin.auth.dto.AdminLoginRequest;
import com.survey.meetorsolo.domain.admin.auth.service.AdminLoginService;
import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.service.AuthCookieFactory;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 슈퍼관리자 ID/PW 로그인({@code docs/30}).
 *
 * <p>경로를 {@code /api/admin/...}이 아니라 {@code /api/auth/admin/login}에 둔다. 세 가지가
 * 모두 이 접두에 맞춰져 있기 때문이다.
 *
 * <ol>
 *   <li>{@code WebMvcConfig}가 {@code /api/auth/**}를 {@code MemberAccessInterceptor}에서
 *       제외한다. 제재된 소셜 계정의 cookie가 브라우저에 남아 있으면 관리자 로그인 시도
 *       자체가 제재 예외로 막힌다.</li>
 *   <li>Frontend {@code apiClient}가 이 접두의 401에서 자동 refresh 재귀를 막는다.</li>
 *   <li>{@code SecurityConfig}의 permitAll 목록과 일관된다.</li>
 * </ol>
 *
 * <p>로그아웃과 token 갱신은 {@code AuthController}의 기존 endpoint를 그대로 쓴다. 발급되는
 * cookie가 OAuth 로그인과 동일하기 때문이다.
 */
@RestController
public class AdminAuthController {

    private final AdminLoginService adminLogin;
    private final AuthCookieFactory cookies;

    public AdminAuthController(
            AdminLoginService adminLogin,
            @Value("${app.auth.cookie-secure}") boolean secureCookies
    ) {
        this.adminLogin = adminLogin;
        this.cookies = new AuthCookieFactory(secureCookies);
    }

    /**
     * 성공하면 OAuth 로그인과 같은 session cookie를 발급한다.
     *
     * <p>응답 body에 token을 담지 않는다. HttpOnly cookie로만 전달해야 스크립트가 읽을 수 없다.
     */
    @PostMapping("/api/auth/admin/login")
    public ResponseEntity<ApiResponse<Void>> login(@Valid @RequestBody AdminLoginRequest request) {
        AuthTokenResponse tokens = adminLogin.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.token(
                        AuthCookieFactory.ACCESS_TOKEN,
                        tokens.accessToken(),
                        Duration.ofSeconds(tokens.accessTokenExpiresInSeconds())).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.token(
                        AuthCookieFactory.REFRESH_TOKEN,
                        tokens.refreshToken(),
                        Duration.ofSeconds(tokens.refreshTokenExpiresInSeconds())).toString())
                .body(ApiResponse.success(null));
    }
}
