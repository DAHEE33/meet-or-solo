package com.survey.meetorsolo.domain.admin.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardPopularFestivalResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardRecentInquiryResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardRecentReportResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardStatsResponse;
import com.survey.meetorsolo.domain.admin.dashboard.service.AdminDashboardService;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminDashboardController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminDashboardControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean AdminDashboardService dashboard;

    @Test
    void cookie가_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 관리자가_아니면_403이다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("token")).thenReturn(1L);
        when(dashboard.getStats(1L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/admin/dashboard/stats").cookie(new Cookie("access_token", "token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 집계를_반환한다() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-09-18T10:00:00+09:00");
        when(jwtProvider.getMemberIdFromAccessToken("admin-token")).thenReturn(2L);
        when(dashboard.getStats(2L)).thenReturn(new AdminDashboardStatsResponse(
                120L, 7L, 430L,
                List.of(new AdminDashboardPopularFestivalResponse(1L, "강릉커피축제", 41L)),
                List.of(new AdminDashboardRecentReportResponse(9L, "NO_SHOW", "SUBMITTED", "홍길동", createdAt)),
                List.of(new AdminDashboardRecentInquiryResponse(5L, "BUG", "체크인이 안 돼요", "RECEIVED", createdAt))));

        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .cookie(new Cookie("access_token", "admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMemberCount").value(120))
                .andExpect(jsonPath("$.data.todayMatchCount").value(7))
                .andExpect(jsonPath("$.data.totalCheckinCount").value(430))
                .andExpect(jsonPath("$.data.popularFestivals[0].title").value("강릉커피축제"))
                .andExpect(jsonPath("$.data.popularFestivals[0].checkinCount").value(41))
                .andExpect(jsonPath("$.data.recentReports[0].reasonCode").value("NO_SHOW"))
                .andExpect(jsonPath("$.data.recentReports[0].reportedNickname").value("홍길동"))
                .andExpect(jsonPath("$.data.recentInquiries[0].title").value("체크인이 안 돼요"))
                // 신고 본문은 암호화 대상이라 대시보드 응답에 어떤 형태로도 담지 않는다.
                .andExpect(jsonPath("$.data.recentReports[0].detail").doesNotExist());
    }
}
