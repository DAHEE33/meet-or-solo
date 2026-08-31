package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FestivalDetailResponse(
        Long id,
        String contentId,
        String title,
        String address,
        String regionCode,
        String sigunguCode,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FestivalStatus status,
        BigDecimal mapX,
        BigDecimal mapY,
        String originImageUrl,
        String thumbnailUrl,
        String intro,
        List<FestivalInfoItem> infoItems,
        List<FestivalProgramItem> programs
) {
}
