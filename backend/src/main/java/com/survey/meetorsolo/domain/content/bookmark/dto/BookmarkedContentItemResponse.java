package com.survey.meetorsolo.domain.content.bookmark.dto;

import com.survey.meetorsolo.domain.content.support.ContentTargetType;
import com.survey.meetorsolo.domain.festival.dto.FestivalListItemResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListItemResponse;
import java.time.OffsetDateTime;

/**
 * 내 찜 목록 항목 1건.
 *
 * <p>대상 정보를 평탄화하지 않고 <b>기존 목록 DTO를 그대로 품는다</b> — 찜 목록 화면이
 * {@code FestivalListItem}/{@code ExploreSpotItem} 카드 컴포넌트를 변환 없이 재사용할 수 있게
 * 하려는 것이다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 7.3).
 *
 * <p>{@code festival}과 {@code tourPlace} 중 정확히 하나만 값을 가지며, 어느 쪽인지는
 * {@code targetType}이 알려준다. 화면은 각 DTO의 {@code status}로 {@code INACTIVE}·종료 배지를
 * 표시한다.
 */
public record BookmarkedContentItemResponse(
        OffsetDateTime bookmarkedAt,
        ContentTargetType targetType,
        FestivalListItemResponse festival,
        TourPlaceListItemResponse tourPlace
) {

    public static BookmarkedContentItemResponse ofFestival(
            OffsetDateTime bookmarkedAt,
            FestivalListItemResponse festival
    ) {
        return new BookmarkedContentItemResponse(
                bookmarkedAt, ContentTargetType.FESTIVAL, festival, null);
    }

    public static BookmarkedContentItemResponse ofTourPlace(
            OffsetDateTime bookmarkedAt,
            TourPlaceListItemResponse tourPlace
    ) {
        return new BookmarkedContentItemResponse(
                bookmarkedAt, ContentTargetType.TOUR_PLACE, null, tourPlace);
    }
}
