package com.survey.meetorsolo.external.tourapi.dto;

import java.util.List;

public record TourApiPage<T>(
        int numOfRows,
        int pageNo,
        int totalCount,
        List<T> items
) {

    public TourApiPage {
        items = List.copyOf(items);
    }
}
