package com.survey.meetorsolo.domain.festival.dto;

/**
 * 솔로 코스의 스톱 1건. {@code distanceFromPreviousMeters}/{@code walkMinutesFromPrevious}의
 * "이전"은 1번 스톱 기준으로는 축제 좌표를 의미한다.
 */
public record SoloCourseStopResponse(
        int order,
        Long id,
        String title,
        String address,
        String contentTypeId,
        String imageUrl,
        long distanceFromPreviousMeters,
        int walkMinutesFromPrevious,
        int estimatedStayMinutes
) {
}
