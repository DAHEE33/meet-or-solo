package com.survey.meetorsolo.domain.matching.dto;

public record MatchCancellationResponse(
        long groupId,
        String memberStatus,
        String groupStatus,
        boolean groupContinues,
        int currentMemberCount
) {
}
