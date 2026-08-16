package com.survey.meetorsolo.domain.safety.report.admin.dto;

import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import java.time.OffsetDateTime;

public record AdminReportListItemResponse(
        long reportId,
        Long groupId,
        MatchReportReasonCode reasonCode,
        AdminReportStatus status,
        AdminReportMemberSummaryResponse reporter,
        AdminReportMemberSummaryResponse reportedMember,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
