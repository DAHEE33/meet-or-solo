package com.survey.meetorsolo.domain.tourplace.dto;

import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;

public record TourPlaceListItemResponse(
        Long id,
        String contentId,
        String contentTypeId,
        String title,
        String address,
        TourPlaceStatus status,
        String imageUrl
) {
}
