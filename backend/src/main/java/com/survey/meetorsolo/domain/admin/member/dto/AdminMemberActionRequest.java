package com.survey.meetorsolo.domain.admin.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminMemberActionRequest(
        @NotNull AdminMemberActionType action,
        @NotNull AdminMemberActionReasonCode reasonCode,
        @Size(max = 500) String reasonNote,
        AdminSuspensionDuration suspensionDuration,
        Long reportId,
        @NotNull AdminMemberStatus expectedStatus
) {
}
