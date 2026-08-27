package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.time.LocalDate;

/**
 * 목록 조회 전용 프로젝션이다. {@code Festival} 엔티티 전체(특히 목록 화면에 쓰이지 않는
 * {@code raw_data} JSONB, 좌표 등)를 읽지 않고 목록에 필요한 컬럼만 가져오기 위한 쿼리 결과
 * 타입이며 JPA 엔티티가 아니다. {@code FestivalQueryService#getActiveFestivals}만 사용한다.
 */
public record FestivalSummary(
        Long id,
        String contentId,
        String title,
        String address,
        String regionCode,
        String sigunguCode,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FestivalStatus status
) {
}
