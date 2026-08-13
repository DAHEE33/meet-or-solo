package com.survey.meetorsolo.domain.safety.report.dto;

import java.time.OffsetDateTime;

public record MatchReportResponse(
        long reportId,
        long groupId,
        long reportedMemberId,
        MatchReportReasonCode reasonCode,
        String status,
        OffsetDateTime createdAt
) {
}
