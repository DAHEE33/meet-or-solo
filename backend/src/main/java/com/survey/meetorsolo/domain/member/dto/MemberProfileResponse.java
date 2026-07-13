package com.survey.meetorsolo.domain.member.dto;

import java.util.List;

public record MemberProfileResponse(
        Long memberId,
        String nickname,
        String email,
        String intro,
        String gender,
        String ageRange,
        String status,
        List<TravelStyleResponse> travelStyles
) {
}
