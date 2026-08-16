package com.survey.meetorsolo.domain.safety.report.admin.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.report.admin.dto.*;
import com.survey.meetorsolo.domain.safety.report.admin.service.AdminReportService;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminReportController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminReportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean AdminReportService reports;

    @Test
    void 모든_endpoint는_cookie가_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/reports/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/reports/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"REVIEWING\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 목록_query와_cursor를_service에_전달한다() throws Exception {
        authenticated();
        when(reports.list(2L, "SUBMITTED", "SAFETY", "2026-08-01T00:00:00+09:00",
                "2026-09-01T00:00:00+09:00", "opaque", 10)).thenReturn(
                new AdminReportPageResponse(List.of(), new AdminReportPaginationResponse(10, false, null)));

        mockMvc.perform(get("/api/admin/reports")
                        .cookie(cookie())
                        .queryParam("status", "SUBMITTED")
                        .queryParam("reason", "SAFETY")
                        .queryParam("createdFrom", "2026-08-01T00:00:00+09:00")
                        .queryParam("createdTo", "2026-09-01T00:00:00+09:00")
                        .queryParam("cursor", "opaque")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.pagination.size").value(10));
    }

    @Test
    void 상세는_민감_필드_없이_반환한다() throws Exception {
        authenticated();
        when(reports.detail(2L, 31L)).thenReturn(detail(AdminReportStatus.SUBMITTED));

        mockMvc.perform(get("/api/admin/reports/31").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(31L))
                .andExpect(jsonPath("$.data.reporter.nickname").value("신고자"))
                .andExpect(jsonPath("$.data.reportedMember.nickname").value("피신고자"))
                .andExpect(jsonPath("$.data.detailEncrypted").doesNotExist())
                .andExpect(jsonPath("$.data.reporter.email").doesNotExist())
                .andExpect(jsonPath("$.data.reportedMember.providerUserId").doesNotExist());
    }

    @Test
    void 상태_변경_body에는_targetStatus만_사용한다() throws Exception {
        authenticated();
        when(reports.changeStatus(2L, 31L, AdminReportTargetStatus.RESOLVED))
                .thenReturn(detail(AdminReportStatus.RESOLVED));

        mockMvc.perform(patch("/api/admin/reports/31/status").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus":"RESOLVED","adminMemberId":999,"targetMemberId":999}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    void 허용하지_않는_targetStatus는_400이다() throws Exception {
        authenticated();
        mockMvc.perform(patch("/api/admin/reports/31/status").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"ACTION_TAKEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    private void authenticated() {
        when(jwtProvider.getMemberIdFromAccessToken("admin-token")).thenReturn(2L);
    }

    private Cookie cookie() {
        return new Cookie("access_token", "admin-token");
    }

    private AdminReportDetailResponse detail(AdminReportStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-15T10:00:00+09:00");
        return new AdminReportDetailResponse(
                31L,
                new AdminReportGroupSummaryResponse(41L, "COMPLETED", now.minusHours(1)),
                MatchReportReasonCode.SAFETY,
                status,
                new AdminReportMemberSummaryResponse(21L, "신고자", null, "ACTIVE"),
                new AdminReportMemberSummaryResponse(22L, "피신고자", null, "ACTIVE"),
                now, now, status.isTerminal() ? now : null);
    }
}
