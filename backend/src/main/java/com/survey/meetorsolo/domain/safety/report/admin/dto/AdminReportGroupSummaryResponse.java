package com.survey.meetorsolo.domain.safety.report.admin.dto;

import java.time.OffsetDateTime;

public record AdminReportGroupSummaryResponse(
        Long groupId,
        String status,
        OffsetDateTime confirmedAt
) {
}
