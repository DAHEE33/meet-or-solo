package com.survey.meetorsolo.domain.admin.dashboard.dto;

/**
 * 체크인 수 기준 인기 축제 한 건.
 *
 * <p>관광지(tour_places)가 아니라 축제다. 체크인은 축제에만 존재한다
 * (festival_checkins.festival_id). 관광지에는 북마크만 있고 현장 방문 기록이 없다.
 */
public record AdminDashboardPopularFestivalResponse(
        long festivalId,
        String title,
        long checkinCount
) {
}
