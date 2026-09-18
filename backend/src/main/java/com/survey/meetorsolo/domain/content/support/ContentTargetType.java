package com.survey.meetorsolo.domain.content.support;

/**
 * 찜·댓글의 대상 종류. 축제와 관광지 두 종류로 고정이며, DB에는 {@code target_type} 컬럼이 아니라
 * nullable FK 2개로 표현한다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 3.1).
 */
public enum ContentTargetType {
    FESTIVAL,
    TOUR_PLACE
}
