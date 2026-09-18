package com.survey.meetorsolo.domain.safety.report.admin.dto;

import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import java.time.OffsetDateTime;

public record AdminReportDetailResponse(
        long reportId,
        AdminReportGroupSummaryResponse group,
        MatchReportReasonCode reasonCode,
        AdminReportStatus status,
        AdminReportMemberSummaryResponse reporter,
        AdminReportMemberSummaryResponse reportedMember,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt
) {
}
