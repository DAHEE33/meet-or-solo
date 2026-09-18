package com.survey.meetorsolo.domain.festival.dto;

import java.util.List;

public record FestivalListResponse(
        List<FestivalListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        /**
         * 이 응답을 받는 사람이 로그인했는가.
         *
         * <p>목록에서 바로 찜을 누를 수 있게 되면서 필요해졌다 — 비로그인 사용자가 하트를 누르면
         * 요청을 보내지 않고 화면이 직접 {@code /login}으로 가야 하는데, 로그인 여부를 알려고
         * {@code GET /api/members/me}를 부르면 공개 목록에서 401을 받아 화면째로 튕긴다
         * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.1). 상세 화면의 {@code engagement}
         * 응답이 {@code viewer.loggedIn}을 함께 주는 것과 같은 이유다.
         */
        boolean viewerLoggedIn
) {

    public FestivalListResponse {
        items = List.copyOf(items);
    }
}
