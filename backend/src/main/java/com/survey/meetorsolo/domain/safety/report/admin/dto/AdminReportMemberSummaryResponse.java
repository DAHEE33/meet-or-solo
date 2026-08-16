package com.survey.meetorsolo.domain.safety.report.admin.dto;

public record AdminReportMemberSummaryResponse(
        long memberId,
        String nickname,
        String profileImageUrl,
        String memberStatus
) {
}
