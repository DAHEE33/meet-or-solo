package com.survey.meetorsolo.domain.content.comment.dto;

import jakarta.validation.constraints.NotNull;

/** 좋아요 토글 요청. 상태를 그대로 받는 {@code PUT}이므로 연타해도 결과가 같다(docs/27 5.4). */
public record ContentCommentLikeRequest(@NotNull Boolean liked) {
}
