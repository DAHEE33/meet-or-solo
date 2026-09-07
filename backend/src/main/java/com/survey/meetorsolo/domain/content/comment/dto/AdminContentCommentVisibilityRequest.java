package com.survey.meetorsolo.domain.content.comment.dto;

import jakarta.validation.constraints.NotNull;

/** 관리자 댓글 숨김/재공개 요청(docs/27 5.6). */
public record AdminContentCommentVisibilityRequest(@NotNull Boolean visible) {
}
