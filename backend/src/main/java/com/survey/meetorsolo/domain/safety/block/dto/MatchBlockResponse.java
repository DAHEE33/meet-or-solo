package com.survey.meetorsolo.domain.safety.block.dto;

import java.time.OffsetDateTime;

public record MatchBlockResponse(
        long blockId,
        long blockedMemberId,
        OffsetDateTime createdAt
) {
}
