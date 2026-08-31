package com.survey.meetorsolo.domain.festival.dto;

import java.util.List;

public record FestivalListResponse(
        List<FestivalListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public FestivalListResponse {
        items = List.copyOf(items);
    }
}
