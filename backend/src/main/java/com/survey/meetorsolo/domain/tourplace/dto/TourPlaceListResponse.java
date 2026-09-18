package com.survey.meetorsolo.domain.tourplace.dto;

import java.util.List;

public record TourPlaceListResponse(
        List<TourPlaceListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        /**
         * 이 응답을 받는 사람이 로그인했는가. 축제 목록과 같은 이유다 — 비로그인이 하트를
         * 누르면 요청 없이 {@code /login}으로 보내야 하는데, 로그인 여부를 알려고 별도 API를
         * 부르면 공개 목록에서 401을 받아 화면째로 튕긴다(docs/27 2.1).
         */
        boolean viewerLoggedIn
) {

    public TourPlaceListResponse {
        items = List.copyOf(items);
    }
}
