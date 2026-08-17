package com.survey.meetorsolo.domain.admin.member.dto;

import java.time.OffsetDateTime;

public record AdminMemberReportHistoryResponse(
        long reportId,
        String reasonCode,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {
}
