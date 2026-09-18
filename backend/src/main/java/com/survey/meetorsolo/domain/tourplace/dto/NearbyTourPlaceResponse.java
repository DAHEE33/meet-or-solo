package com.survey.meetorsolo.domain.tourplace.dto;

/** 축제 상세·홈 화면 "축제와 함께 둘러보기" 등에서 사용하는, 기준 좌표 주변 관광지 응답. */
public record NearbyTourPlaceResponse(
        Long id,
        String title,
        String address,
        String contentTypeId,
        String imageUrl,
        long distanceMeters,
        /** 이 관광지를 찜한 회원 수. 화면에는 "좋아요 수"로 표시한다. */
        long bookmarkCount,
        /** 공개({@code VISIBLE}) 댓글 수. */
        long commentCount,
        /**
         * 이 열람자가 찜했는가. 홈 화면 "축제와 함께 둘러보기" 카드에서 바로 찜을 토글하므로
         * 필요하다. 비로그인이면 항상 {@code false}다.
         */
        boolean bookmarkedByMe
) {
}
