package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import java.time.OffsetDateTime;
import java.util.List;

public record MatchGroupResponse(
        Long groupId,
        Long festivalId,
        String status,
        Integer confirmedMemberCount,
        OffsetDateTime confirmedAt,
        List<MatchGroupMemberResponse> members
) {

    public static MatchGroupResponse from(
            MatchGroup group,
            List<MatchGroupMemberResponse> members
    ) {
        return new MatchGroupResponse(
                group.getId(),
                group.getFestivalId(),
                group.getStatus(),
                members.size(),
                group.getConfirmedAt(),
                List.copyOf(members)
        );
    }
}
