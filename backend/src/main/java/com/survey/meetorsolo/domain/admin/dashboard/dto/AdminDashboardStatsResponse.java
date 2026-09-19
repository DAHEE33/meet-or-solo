package com.survey.meetorsolo.domain.admin.dashboard.dto;

import java.util.List;

/**
 * 관리자 대시보드 집계 응답.
 *
 * <p>신고와 문의를 한 목록으로 합치지 않고 따로 담는다. 두 값은 식별자도 상태 enum도 다른
 * 별개 도메인이라, 합치면 한쪽에만 있는 field가 전부 nullable이 된다. 화면이 하나의
 * 최신순 목록으로 보여주더라도 그 병합은 표현의 문제이므로 frontend에서 한다.
 */
public record AdminDashboardStatsResponse(
        long totalMemberCount,
        long todayMatchCount,
        long totalCheckinCount,
        List<AdminDashboardPopularFestivalResponse> popularFestivals,
        List<AdminDashboardRecentReportResponse> recentReports,
        List<AdminDashboardRecentInquiryResponse> recentInquiries
) {
}
