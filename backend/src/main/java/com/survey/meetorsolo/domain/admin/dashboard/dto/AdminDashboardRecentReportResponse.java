package com.survey.meetorsolo.domain.admin.dashboard.dto;

import java.time.OffsetDateTime;

/**
 * 대시보드에 띄우는 최근 신고 한 건.
 *
 * <p>신고 본문({@code reports.detail_encrypted})은 담지 않는다. 대시보드는 목록을 훑는
 * 자리이고 복호화가 필요한 값이라, 사유 코드까지만 노출한 뒤 상세는 {@code /admin/reports}로
 * 넘긴다. 신고자 identity도 담지 않는다(신고자 보호, docs/06).
 */
public record AdminDashboardRecentReportResponse(
        long reportId,
        String reasonCode,
        String status,
        String reportedNickname,
        OffsetDateTime createdAt
) {
}
