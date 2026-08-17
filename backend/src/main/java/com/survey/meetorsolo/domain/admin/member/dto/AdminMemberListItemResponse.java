package com.survey.meetorsolo.domain.admin.member.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminMemberListItemResponse(
        long memberId,
        String nickname,
        String profileImageUrl,
        String role,
        AdminMemberStatus status,
        int penaltyScore,
        BigDecimal mannerTemperature,
        OffsetDateTime suspendedUntil,
        OffsetDateTime createdAt
) {
}
