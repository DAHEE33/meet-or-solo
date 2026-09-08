package com.survey.meetorsolo.domain.content.bookmark.repository;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 내 찜 목록(축제 탭) 조회 프로젝션.
 *
 * <p>필드는 목록 화면이 쓰는 {@code FestivalListItemResponse}와 1:1로 대응한다(대표 이미지는
 * 별도 테이블이라 호출부가 채운다). 중첩 constructor expression을 쓰지 않고 평탄하게 두는 이유는
 * JPQL 중첩 {@code new}가 구현체 의존적이라서다 — 조립은 service가 한다.
 */
public record BookmarkedFestivalRow(
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
        BigDecimal mapY,
        OffsetDateTime bookmarkedAt
) {
}
