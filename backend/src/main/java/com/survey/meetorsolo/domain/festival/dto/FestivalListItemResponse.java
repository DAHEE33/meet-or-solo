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
        BigDecimal mapY
) {
}
