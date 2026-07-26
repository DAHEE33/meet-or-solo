package com.survey.meetorsolo.domain.matching.event;

public record MatchingPoolEnteredEvent(
        long poolId,
        long memberId,
        long festivalId
) {
}
