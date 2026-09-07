package com.survey.meetorsolo.domain.content.support;

import java.util.Objects;

/**
 * 찜·댓글이 가리키는 대상 1건. DB의 nullable FK 2개를 코드에서 하나의 값으로 다루기 위한 타입이며,
 * {@link #festivalId()}와 {@link #tourPlaceId()} 중 정확히 하나만 값을 가진다
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 3.1의 {@code chk_..._target}과 같은 규칙).
 */
public record ContentTarget(ContentTargetType type, long id) {

    public ContentTarget {
        Objects.requireNonNull(type, "type");
        if (id <= 0) {
            throw new IllegalArgumentException("대상 id는 양수여야 합니다.");
        }
    }

    public static ContentTarget festival(long festivalId) {
        return new ContentTarget(ContentTargetType.FESTIVAL, festivalId);
    }

    public static ContentTarget tourPlace(long tourPlaceId) {
        return new ContentTarget(ContentTargetType.TOUR_PLACE, tourPlaceId);
    }

    public boolean isFestival() {
        return type == ContentTargetType.FESTIVAL;
    }

    /** 축제 대상이면 축제 id, 관광지 대상이면 {@code null}. 그대로 FK 컬럼 값이 된다. */
    public Long festivalId() {
        return isFestival() ? id : null;
    }

    /** 관광지 대상이면 관광지 id, 축제 대상이면 {@code null}. 그대로 FK 컬럼 값이 된다. */
    public Long tourPlaceId() {
        return isFestival() ? null : id;
    }
}
