package com.survey.meetorsolo.domain.content.comment.dto;

import java.util.List;

/**
 * 댓글 목록. 기존 공개 목록 API와 같은 offset 페이지 형태라 frontend의 {@code useInfiniteList}를
 * 그대로 재사용한다.
 *
 * <p>{@code totalElements}가 화면의 {@code 댓글 N} 표시에 쓰인다(docs/27 5.5).
 */
public record ContentCommentListResponse(
        List<ContentCommentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public ContentCommentListResponse {
        items = List.copyOf(items);
    }
}
