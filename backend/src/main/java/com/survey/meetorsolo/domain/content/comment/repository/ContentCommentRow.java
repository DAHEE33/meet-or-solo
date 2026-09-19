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
        /** 카카오·네이버가 준 외부 URL. 직접 올린 사진이 있으면 그쪽이 우선이다. */
        String profileImageUrl,
        /** 직접 올린 사진의 object key. 값 자체는 응답에 담지 않고 URL을 만드는 데만 쓴다. */
        String profileImageObjectKey,
        String body,
        Integer likeCount,
        OffsetDateTime createdAt
) {
}
