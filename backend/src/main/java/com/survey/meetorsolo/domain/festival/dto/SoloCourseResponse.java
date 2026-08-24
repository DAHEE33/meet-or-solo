package com.survey.meetorsolo.domain.festival.dto;

import java.util.List;

public record SoloCourseResponse(
        SoloCourseType type,
        int totalWalkMinutes,
        int totalStayMinutes,
        int totalDurationMinutes,
        List<SoloCourseStopResponse> stops
) {

    public static SoloCourseResponse of(SoloCourseType type, List<SoloCourseStopResponse> stops) {
        int totalWalkMinutes = stops.stream().mapToInt(SoloCourseStopResponse::walkMinutesFromPrevious).sum();
        int totalStayMinutes = stops.stream().mapToInt(SoloCourseStopResponse::estimatedStayMinutes).sum();
        return new SoloCourseResponse(type, totalWalkMinutes, totalStayMinutes, totalWalkMinutes + totalStayMinutes, stops);
    }
}
