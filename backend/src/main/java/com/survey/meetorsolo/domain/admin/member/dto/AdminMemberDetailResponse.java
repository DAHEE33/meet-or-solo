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
        /** 최근 30일 누적 유효 신고 건수. 같은 만남의 사유별 중복은 1건으로 압축한다. */
        long recentValidReportCount,
        /** 누적 유효 신고가 임계에 도달해 이용 제한을 검토해야 하는 회원인지. */
        boolean safetyReviewRequired,
        List<AdminMemberReportHistoryResponse> reports,
        List<AdminMemberActionHistoryResponse> actions
) {
}
