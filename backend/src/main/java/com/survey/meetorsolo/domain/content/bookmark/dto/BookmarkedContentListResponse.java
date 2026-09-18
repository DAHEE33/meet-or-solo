package com.survey.meetorsolo.domain.content.bookmark.dto;

import java.util.List;

/** 기존 공개 목록 API와 같은 offset 페이지 형태로 통일한다(docs/27 4). */
public record BookmarkedContentListResponse(
        List<BookmarkedContentItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public BookmarkedContentListResponse {
        items = List.copyOf(items);
    }
}
