package com.survey.meetorsolo.domain.content.comment.dto;

/** 토글 후 실제 상태. {@code likeCount}는 DB에서 다시 읽은 값이다. */
public record ContentCommentLikeResponse(boolean liked, int likeCount) {
}
