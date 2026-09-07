package com.survey.meetorsolo.domain.content.bookmark.repository;

import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import java.time.OffsetDateTime;

/**
 * 내 찜 목록(관광지 탭) 조회 프로젝션. 필드는 {@code TourPlaceListItemResponse}와 1:1로
 * 대응하며 조립은 service가 한다.
 */
public record BookmarkedTourPlaceRow(
        Long id,
        String contentId,
        String contentTypeId,
        String title,
        String address,
        TourPlaceStatus status,
        String imageUrl,
        OffsetDateTime bookmarkedAt
) {
}
