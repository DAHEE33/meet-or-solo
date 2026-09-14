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
        OffsetDateTime createdAt,
        /** 테스트 계정이면 축제 체크인의 GPS 반경·정확도 검증을 면제받는다. */
        boolean testAccount
) {
}
