package com.survey.meetorsolo.domain.admin.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminSessionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminSessionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean AdminAuthorizationService authorization;

    @Test
    void cookie가_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void ADMIN의_최소_session만_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("admin-token")).thenReturn(2L);
        when(authorization.requireAdmin(2L)).thenReturn(
                new AdminAuthorizationService.AdminMember(2L, "관리자", "ADMIN"));

        mockMvc.perform(get("/api/admin/me").cookie(new Cookie("access_token", "admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(2L))
                .andExpect(jsonPath("$.data.nickname").value("관리자"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.provider").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }
}
