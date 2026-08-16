package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository.ActiveGroupMemberProjection;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record MatchGroupMemberResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String status,
        Integer arrivalMinutes,
        OffsetDateTime arrivalTimeSelectedAt,
        OffsetDateTime arrivedAt
) {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public MatchGroupMemberResponse(
            Long memberId,
            String nickname,
            String profileImageUrl,
            String status,
            Integer arrivalMinutes,
            OffsetDateTime arrivalTimeSelectedAt
    ) {
        this(
                memberId,
                nickname,
                profileImageUrl,
                status,
                arrivalMinutes,
                arrivalTimeSelectedAt,
                null
        );
    }

    public static MatchGroupMemberResponse from(ActiveGroupMemberProjection member) {
        return new MatchGroupMemberResponse(
                member.getMemberId(),
                member.getNickname(),
                normalize(member.getProfileImageUrl()),
                member.getStatus(),
                member.getArrivalMinutes(),
                member.getArrivalTimeSelectedAt() == null
                        ? null
                        : member.getArrivalTimeSelectedAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                member.getArrivedAt() == null
                        ? null
                        : member.getArrivedAt().atZone(KOREA_ZONE).toOffsetDateTime()
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
