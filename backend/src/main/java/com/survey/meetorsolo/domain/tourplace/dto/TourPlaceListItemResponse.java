package com.survey.meetorsolo.domain.tourplace.dto;

import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;

public record TourPlaceListItemResponse(
        Long id,
        String contentId,
        String contentTypeId,
        String title,
        String address,
        TourPlaceStatus status,
        String imageUrl,
        /**
         * 이 관광지를 찜한 회원 수. 화면에는 "좋아요 수"로 표시한다 — 콘텐츠 단위 반응은 찜
         * 하나뿐이고, 댓글 좋아요는 댓글 단위라 목록 지표가 되지 못한다.
         */
        long bookmarkCount,
        /** 공개({@code VISIBLE}) 댓글 수. 상세 화면의 commentCount와 같은 기준이다. */
        long commentCount,
        /**
         * 이 열람자가 찜했는가. 목록에서 바로 찜을 토글하므로 하트를 채울지 판단하려면 필요하다.
         * 비로그인이면 항상 {@code false}다(목록 조회는 비로그인에도 200이다).
         */
        boolean bookmarkedByMe
) {
}
