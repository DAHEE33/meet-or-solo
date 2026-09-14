package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.time.LocalDate;

/** 관광지 상세 "이 장소 주변에서 열리는 축제" 등에서 사용하는, 기준 좌표 주변 축제 응답. */
public record NearbyFestivalResponse(
        Long id,
        String title,
        String address,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FestivalStatus status,
        String thumbnailUrl,
        long distanceMeters,
        /** 이 축제를 찜한 회원 수. 화면에는 "좋아요 수"로 표시한다. */
        long bookmarkCount,
        /** 공개({@code VISIBLE}) 댓글 수. */
        long commentCount,
        /**
         * 이 열람자가 찜했는가. 관광지 상세의 "주변에서 열리는 축제" 카드에서 바로 찜을
         * 토글하므로 필요하다. 비로그인이면 항상 {@code false}다.
         */
        boolean bookmarkedByMe
) {
}
