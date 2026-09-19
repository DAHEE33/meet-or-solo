package com.survey.meetorsolo.domain.admin.dashboard.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardPopularFestivalResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardRecentInquiryResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardRecentReportResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardStatsResponse;
import com.survey.meetorsolo.domain.admin.dashboard.repository.AdminDashboardRepository;
import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminDashboardServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
    private final AdminDashboardRepository dashboard = mock(AdminDashboardRepository.class);

    private AdminDashboardService serviceAt(String instant) {
        return new AdminDashboardService(
                authorization, dashboard, Clock.fixed(Instant.parse(instant), SEOUL));
    }

    @Test
    void 관리자가_아니면_집계를_읽지_않는다() {
        when(authorization.requireAdmin(1L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> serviceAt("2026-09-18T00:00:00Z").getStats(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(dashboard);
    }

    @Test
    void 오늘_매칭은_서울_기준_오늘_0시부터_센다() {
        // UTC 2026-09-17T20:00Z = 서울 2026-09-18 05:00. 경계는 서울 0시(= 2026-09-17T15:00Z)다.
        serviceAt("2026-09-17T20:00:00Z").getStats(2L);

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(dashboard).countMatchGroupsConfirmedSince(since.capture());
        assertThat(since.getValue().toInstant()).isEqualTo(Instant.parse("2026-09-17T15:00:00Z"));
    }

    @Test
    void 서울_기준_자정_직후에도_그날_0시를_경계로_쓴다() {
        // UTC 2026-09-17T15:30Z = 서울 2026-09-18 00:30. 어제 0시로 돌아가면 안 된다.
        serviceAt("2026-09-17T15:30:00Z").getStats(2L);

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(dashboard).countMatchGroupsConfirmedSince(since.capture());
        assertThat(since.getValue().toInstant()).isEqualTo(Instant.parse("2026-09-17T15:00:00Z"));
    }

    @Test
    void 집계값을_그대로_응답에_담는다() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-18T10:00:00+09:00");
        when(dashboard.countMembers()).thenReturn(120L);
        when(dashboard.countMatchGroupsConfirmedSince(any())).thenReturn(7L);
        when(dashboard.countCheckins()).thenReturn(430L);
        when(dashboard.findPopularFestivals(anyInt())).thenReturn(List.of(
                new AdminDashboardPopularFestivalResponse(1L, "강릉커피축제", 41L)));
        when(dashboard.findRecentReports(anyInt())).thenReturn(List.of(
                new AdminDashboardRecentReportResponse(9L, "NO_SHOW", "SUBMITTED", "홍길동", now)));
        when(dashboard.findRecentInquiries(anyInt())).thenReturn(List.of(
                new AdminDashboardRecentInquiryResponse(5L, "BUG", "체크인이 안 돼요", "RECEIVED", now)));

        AdminDashboardStatsResponse response = serviceAt("2026-09-18T01:00:00Z").getStats(2L);

        assertThat(response.totalMemberCount()).isEqualTo(120L);
        assertThat(response.todayMatchCount()).isEqualTo(7L);
        assertThat(response.totalCheckinCount()).isEqualTo(430L);
        assertThat(response.popularFestivals()).singleElement()
                .extracting(AdminDashboardPopularFestivalResponse::title).isEqualTo("강릉커피축제");
        assertThat(response.recentReports()).singleElement()
                .extracting(AdminDashboardRecentReportResponse::reasonCode).isEqualTo("NO_SHOW");
        assertThat(response.recentInquiries()).singleElement()
                .extracting(AdminDashboardRecentInquiryResponse::title).isEqualTo("체크인이 안 돼요");
    }

    @Test
    void 신고와_문의를_같은_건수로_읽는다() {
        serviceAt("2026-09-18T01:00:00Z").getStats(2L);

        verify(dashboard).findRecentReports(AdminDashboardService.RECENT_ISSUE_LIMIT);
        verify(dashboard).findRecentInquiries(AdminDashboardService.RECENT_ISSUE_LIMIT);
        verify(dashboard).findPopularFestivals(AdminDashboardService.POPULAR_FESTIVAL_LIMIT);
    }
}
