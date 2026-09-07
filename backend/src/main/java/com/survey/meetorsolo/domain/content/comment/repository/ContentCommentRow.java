package com.survey.meetorsolo.domain.content.comment.repository;

import java.time.OffsetDateTime;

/**
 * 댓글 목록 조회 프로젝션.
 *
 * <p>{@code authorMemberId}는 호출부가 {@code mine}을 계산하기 위한 내부 값이며 응답 DTO로
 * 넘기지 않는다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 6.3).
 *
 * <p>필드를 모두 wrapper 타입으로 둔 것은 다른 repository 프로젝션
 * ({@code RegionAggregate}, {@code TourPlaceListItemResponse})과 같은 방식이다.
 * {@code like_count}는 {@code NOT NULL}이라 {@code null}이 올 수 없다.
 */
public record ContentCommentRow(
        Long id,
        Long authorMemberId,
        String nickname,
        String body,
        Integer likeCount,
        OffsetDateTime createdAt
) {
}
