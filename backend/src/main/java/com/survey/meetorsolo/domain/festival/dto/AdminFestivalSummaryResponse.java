package com.survey.meetorsolo.domain.festival.dto;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 관리자 만남 장소 화면의 축제 검색 결과. 공개 목록({@link FestivalListItemResponse})과 달리
 * 이미지는 담지 않는다. 좌표({@code mapX}=경도, {@code mapY}=위도)는 신규 장소 등록 폼이
 * 카카오맵 좌표 선택기의 초기 중심점으로 쓴다 — 새 만남 장소는 대부분 축제 좌표 근처이므로
 * (자동 시딩도 같은 좌표를 그대로 쓴다, docs/24_ADMIN_MEETING_POINT_MANAGEMENT_DESIGN.md 3장).
 * 분류(진행중/진행예정/마감)는 frontend가 {@code eventStartDate}/{@code eventEndDate}/
 * {@code status}로 계산한다.
 */
public record AdminFestivalSummaryResponse(
        Long id,
        String title,
        String address,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FestivalStatus status,
        BigDecimal mapX,
        BigDecimal mapY
) {
}
