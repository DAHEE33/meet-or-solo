package com.survey.meetorsolo.domain.tourplace.dto;

import java.util.List;

public record TourPlaceListResponse(
        List<TourPlaceListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public TourPlaceListResponse {
        items = List.copyOf(items);
    }
}
