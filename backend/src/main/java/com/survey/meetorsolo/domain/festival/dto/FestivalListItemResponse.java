package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.time.LocalDate;

public record FestivalListItemResponse(
        Long id,
        String contentId,
        String title,
        String address,
        String regionCode,
        String sigunguCode,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FestivalStatus status,
        String originImageUrl,
        String thumbnailUrl
) {
}
