package com.survey.meetorsolo.domain.admin.member.dto;

import java.time.OffsetDateTime;

public record AdminMemberActionHistoryResponse(
        long actionId,
        AdminMemberActionType actionType,
        AdminMemberActionReasonCode reasonCode,
        String reasonNote,
        Long reportId,
        OffsetDateTime createdAt
) {
}
