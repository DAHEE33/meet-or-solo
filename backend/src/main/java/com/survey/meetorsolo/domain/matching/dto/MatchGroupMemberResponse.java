package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository.ActiveGroupMemberProjection;

public record MatchGroupMemberResponse(
        Long memberId,
        String nickname,
        String profileImageUrl
) {

    public static MatchGroupMemberResponse from(ActiveGroupMemberProjection member) {
        return new MatchGroupMemberResponse(
                member.getMemberId(),
                member.getNickname(),
                normalize(member.getProfileImageUrl())
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
