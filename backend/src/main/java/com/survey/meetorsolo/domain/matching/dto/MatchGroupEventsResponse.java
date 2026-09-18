package com.survey.meetorsolo.domain.matching.dto;

import java.util.List;

public record MatchGroupEventsResponse(
        List<MatchGroupEventResponse> events
) {
}
