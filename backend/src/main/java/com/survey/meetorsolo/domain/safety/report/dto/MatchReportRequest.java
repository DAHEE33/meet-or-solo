package com.survey.meetorsolo.domain.safety.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MatchReportRequest(
        @NotNull @Positive Long reportedMemberId,
        @NotNull MatchReportReasonCode reasonCode
) {
}
