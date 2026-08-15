package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import java.time.OffsetDateTime;
import java.util.List;

public record MatchPoolResponse(
        long poolId,
        long festivalId,
        int preferredGroupSize,
        boolean allowMinimumTwo,
        List<String> tags,
        String status,
        OffsetDateTime enteredAt,
        OffsetDateTime searchExpiresAt,
        String terminationReason
) {
    public static MatchPoolResponse from(MatchPool pool) {
        return from(pool, null);
    }

    public static MatchPoolResponse from(MatchPool pool, String terminationReason) {
        return new MatchPoolResponse(
                pool.getId(),
                pool.getFestivalId(),
                pool.getPreferredGroupSize(),
                pool.getAllowMinimumTwo(),
                pool.getTags(),
                pool.getStatus(),
                pool.getEnteredAt(),
                pool.getSearchExpiresAt(),
                terminationReason
        );
    }
}
