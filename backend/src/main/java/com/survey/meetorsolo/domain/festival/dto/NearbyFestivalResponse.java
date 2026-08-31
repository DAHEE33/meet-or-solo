package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.time.LocalDate;

/** 관광지 상세 "이 장소 주변에서 열리는 축제" 등에서 사용하는, 기준 좌표 주변 축제 응답. */
public record NearbyFestivalResponse(
        Long id,
        String title,
        String address,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FestivalStatus status,
        String thumbnailUrl,
        long distanceMeters
) {
}
