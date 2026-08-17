package com.survey.meetorsolo.domain.admin.member.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminMemberDetailResponse(
        long memberId,
        String nickname,
        String profileImageUrl,
        String role,
        AdminMemberStatus status,
        int penaltyScore,
        BigDecimal mannerTemperature,
        OffsetDateTime suspendedAt,
        OffsetDateTime suspendedUntil,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt,
        List<AdminMemberReportHistoryResponse> reports,
        List<AdminMemberActionHistoryResponse> actions
) {
}
