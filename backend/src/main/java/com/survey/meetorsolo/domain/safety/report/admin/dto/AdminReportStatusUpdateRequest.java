package com.survey.meetorsolo.domain.safety.report.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminReportStatusUpdateRequest(@NotNull AdminReportTargetStatus targetStatus) {
}
