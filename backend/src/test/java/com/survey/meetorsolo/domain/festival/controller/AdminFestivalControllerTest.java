package com.survey.meetorsolo.domain.festival.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.festival.dto.AdminFestivalSummaryResponse;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.service.FestivalAdminQueryService;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminFestivalController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminFestivalControllerTest {
    private static final String BASE = "/api/admin/festivals";

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean FestivalAdminQueryService service;

    @Test
    void access_token_cookie가_없으면_401이다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 일반_회원은_403이다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("user-token")).thenReturn(1L);
        when(service.search(1L, null)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get(BASE).cookie(cookie("user-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void ADMIN은_키워드로_종료된_축제도_검색한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("admin-token")).thenReturn(2L);
        when(service.search(2L, "봄")).thenReturn(List.of(response()));

        mockMvc.perform(get(BASE).param("keyword", "봄").cookie(cookie("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("봄맞이 축제"))
                .andExpect(jsonPath("$.data[0].status").value("ENDED"));
    }

    @Test
    void 키워드_없이도_전체를_조회한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("admin-token")).thenReturn(2L);
        when(service.search(2L, null)).thenReturn(List.of(response()));

        mockMvc.perform(get(BASE).cookie(cookie("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10));
    }

    private jakarta.servlet.http.Cookie cookie(String value) {
        return new jakarta.servlet.http.Cookie("access_token", value);
    }

    private AdminFestivalSummaryResponse response() {
        return new AdminFestivalSummaryResponse(10L, "봄맞이 축제", "강원 어딘가",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), FestivalStatus.ENDED,
                new BigDecimal("128.1"), new BigDecimal("37.1"));
    }
}
