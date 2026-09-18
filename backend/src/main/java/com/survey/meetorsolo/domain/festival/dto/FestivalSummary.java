package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 목록 조회 전용 프로젝션이다. {@code Festival} 엔티티 전체(특히 목록 화면에 쓰이지 않는
 * {@code raw_data} JSONB)를 읽지 않고 목록에 필요한 컬럼만 가져오기 위한 쿼리 결과
 * 타입이며 JPA 엔티티가 아니다. {@code FestivalQueryService#getActiveFestivals}와
 * {@code FestivalAdminQueryService#search}가 사용한다.
 *
 * <p>좌표({@code mapX}/{@code mapY})는 홈 화면이 <b>브라우저에서</b> 내 위치와의 거리를 계산해
 * 가장 가까운 축제를 고르기 위해 포함한다. 사용자 좌표를 서버로 보내지 않는 대신 축제 좌표를
 * 클라이언트로 내려보내는 방식이다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 4.1).
 * {@code numeric} 2개라 {@code raw_data}를 제외한 이득을 상쇄하지 않는다.
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
        FestivalStatus status,
        BigDecimal mapX,
        BigDecimal mapY
) {
}
