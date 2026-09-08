package com.survey.meetorsolo.domain.content.comment.dto;

/**
 * 관리자 숨김/재공개 결과.
 *
 * <p>{@code changed}가 {@code false}면 요청한 전환이 실제로 일어나지 않았다는 뜻이다 — 이미 그
 * 상태였거나, 작성자가 삭제한 {@code DELETED} 댓글을 재공개하려 한 경우다(관리자는 되살리지 않는다).
 */
public record AdminContentCommentVisibilityResponse(boolean visible, boolean changed) {
}
