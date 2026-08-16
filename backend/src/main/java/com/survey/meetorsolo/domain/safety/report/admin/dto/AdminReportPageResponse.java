package com.survey.meetorsolo.domain.safety.report.admin.dto;

import java.util.List;

public record AdminReportPageResponse(
        List<AdminReportListItemResponse> items,
        AdminReportPaginationResponse pagination
) {
}
