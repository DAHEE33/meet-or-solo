package com.survey.meetorsolo.domain.admin.safety.dto;

import java.time.OffsetDateTime;

/**
 * 관리자 안전 알림 응답.
 *
 * <p>신고자 identity와 신고 상세는 포함하지 않는다. 관리자는 {@code triggerReportId}로
 * 기존 신고 상세 API를 조회한다.
 */
public record AdminSafetyAlertResponse(
        long alertId,
        AdminSafetyAlertType alertType,
        AdminSafetyAlertStatus status,
        long reportedMemberId,
        String reportedMemberNickname,
        String reportedMemberProfileImageUrl,
        String reportedMemberStatus,
        long triggerReportId,
        int validReportCount,
        OffsetDateTime handledAt,
        OffsetDateTime createdAt
) {
}
