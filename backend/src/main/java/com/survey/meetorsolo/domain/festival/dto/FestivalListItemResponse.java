package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FestivalListItemResponse(
        Long id,
        String contentId,
        String title,
        String address,
        String regionCode,
        String sigunguCode,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FestivalStatus status,
        String originImageUrl,
        String thumbnailUrl,
        /**
         * 홈 화면이 브라우저에서 내 위치와의 거리를 계산하기 위한 좌표. 서버는 사용자 좌표를
         * 받지 않는다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 4.1).
         */
        BigDecimal mapX,
        BigDecimal mapY,
        /**
         * 이 축제를 찜한 회원 수. 화면에는 "좋아요 수"로 표시한다 — 콘텐츠 단위 반응은 찜
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
