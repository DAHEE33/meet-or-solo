package com.survey.meetorsolo.domain.content.comment.entity;

/**
 * 댓글 노출 상태. 저장소에 {@code deleted_at} 단독 soft delete 선례가 없어 status 상태 머신 +
 * 시점 컬럼 관용구를 따른다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 3.3).
 */
public enum ContentCommentStatus {
    /** 공개 중. {@code deleted_at}이 {@code NULL}인 유일한 상태다. */
    VISIBLE,
    /** 작성자가 삭제했다. 관리자가 되살리지 않는다. */
    DELETED,
    /** 관리자가 숨겼다. 관리자가 다시 공개할 수 있다. */
    HIDDEN
}
