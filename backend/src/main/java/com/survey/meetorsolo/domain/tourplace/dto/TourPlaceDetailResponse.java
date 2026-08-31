package com.survey.meetorsolo.domain.tourplace.dto;

import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import java.math.BigDecimal;

public record TourPlaceDetailResponse(
        Long id,
        String contentId,
        String contentTypeId,
        String title,
        String address,
        String tel,
        BigDecimal mapX,
        BigDecimal mapY,
        TourPlaceStatus status,
        String imageUrl
) {
}
