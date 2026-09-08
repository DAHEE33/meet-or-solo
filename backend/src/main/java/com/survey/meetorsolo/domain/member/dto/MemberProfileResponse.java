package com.survey.meetorsolo.domain.member.dto;

import java.util.List;

public record MemberProfileResponse(
        Long memberId,
        String nickname,
        String email,
        String intro,
        String profileImageUrl,
        String gender,
        String ageRange,
        String status,
        /** 제재 중일 때만 채워진다. 화면이 활동 UI를 미리 막는 데 쓴다. */
        MemberSanctionNotice sanction,
        List<TravelStyleResponse> travelStyles
) {
}
