package com.survey.meetorsolo.domain.content.comment.dto;

import com.survey.meetorsolo.domain.content.comment.entity.ContentComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 등록 요청. 길이 상한은 DB의 {@code chk_content_comments_body}와 같은 500자다.
 * 실제 trim 후 재검증은 service가 한다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 5.2).
 */
public record ContentCommentCreateRequest(
        @NotBlank(message = "댓글 내용을 입력해주세요.")
        @Size(max = ContentComment.BODY_MAX_LENGTH, message = "댓글은 500자 이하로 입력해주세요.")
        String body
) {
}
