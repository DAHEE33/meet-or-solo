package com.survey.meetorsolo.domain.content.bookmark.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 찜 토글 요청. {@code POST}/{@code DELETE} 쌍이 아니라 상태를 그대로 받는 {@code PUT} 하나로
 * 두어 멱등성을 보장한다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 4).
 */
public record ContentBookmarkToggleRequest(@NotNull Boolean bookmarked) {
}
