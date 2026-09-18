package com.survey.meetorsolo.domain.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.admin.auth.controller.AdminAuthController;
import com.survey.meetorsolo.domain.admin.auth.dto.AdminLoginRequest;
import com.survey.meetorsolo.domain.admin.auth.service.AdminLoginService;
import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.global.response.ApiResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class AdminAuthControllerTest {

    private final AdminLoginService adminLogin = mock(AdminLoginService.class);
    private final AdminAuthController controller = new AdminAuthController(adminLogin, false);

    private List<String> cookiesOf(ResponseEntity<ApiResponse<Void>> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies == null ? List.of() : cookies;
    }

    @Test
    void 로그인에_성공하면_OAuth와_같은_속성의_session_cookie를_발급한다() {
        AdminLoginRequest request = new AdminLoginRequest("admin", "password");
        when(adminLogin.login(request)).thenReturn(
                new AuthTokenResponse("Bearer", "access-token", "refresh-token", 1800, 20160, 7L, "ACTIVE"));

        ResponseEntity<ApiResponse<Void>> response = controller.login(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(cookiesOf(response)).hasSize(2);
        assertThat(cookiesOf(response).get(0))
                .contains("access_token=access-token")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=1800");
        assertThat(cookiesOf(response).get(1))
                .contains("refresh_token=refresh-token")
                .contains("Max-Age=20160");
    }

    /** token은 HttpOnly cookie로만 나간다. body에 담으면 스크립트가 읽을 수 있다. */
    @Test
    void 응답_body에는_token을_담지_않는다() {
        AdminLoginRequest request = new AdminLoginRequest("admin", "password");
        when(adminLogin.login(request)).thenReturn(
                new AuthTokenResponse("Bearer", "access-token", "refresh-token", 1800, 20160, 7L, "ACTIVE"));

        ResponseEntity<ApiResponse<Void>> response = controller.login(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isNull();
    }
}
