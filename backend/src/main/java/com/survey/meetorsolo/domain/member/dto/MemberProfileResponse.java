package com.survey.meetorsolo.domain.member.dto;

import java.util.List;

public record MemberProfileResponse(
        Long memberId,
        String nickname,
        String gender,
        String ageRange,
        String status,
        List<TravelStyleResponse> travelStyles
) {
}
