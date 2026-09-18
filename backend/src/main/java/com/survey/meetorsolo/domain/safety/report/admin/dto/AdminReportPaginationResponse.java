package com.survey.meetorsolo.domain.safety.report.admin.dto;

public record AdminReportPaginationResponse(int size, boolean hasNext, String nextCursor) {
}
