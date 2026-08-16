package com.survey.meetorsolo.domain.safety.report.admin.dto;

public enum AdminReportStatus {
    SUBMITTED,
    REVIEWING,
    RESOLVED,
    REJECTED,
    ACTION_TAKEN;

    public boolean isTerminal() {
        return this == RESOLVED || this == REJECTED || this == ACTION_TAKEN;
    }
}
