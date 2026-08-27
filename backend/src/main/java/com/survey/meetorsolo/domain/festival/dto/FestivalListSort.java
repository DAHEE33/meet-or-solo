package com.survey.meetorsolo.domain.festival.dto;

import org.springframework.data.domain.Sort;

/**
 * 축제 목록 정렬 기준. "가까운순"은 제공하지 않는다 — 사용자 좌표를 서버로 보내지 않기로
 * 했으므로 서버에는 거리 정렬의 기준점이 없다
 * (docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1).
 *
 * <p>모든 정렬 키에 {@code id}를 tie-breaker로 붙인다. 무한스크롤은 페이지를 여러 번 나눠
 * 요청하므로 정렬이 불안정하면 페이지 경계에서 같은 항목이 중복되거나 누락된다.
 */
public enum FestivalListSort {

    /** 시작일 빠른 순. 기존 동작과 동일한 기본값이다. */
    START_DATE_ASC(Sort.by(Sort.Order.asc("eventStartDate"), Sort.Order.asc("id"))),

    /** 종료 임박 순. */
    END_DATE_ASC(Sort.by(Sort.Order.asc("eventEndDate"), Sort.Order.asc("id"))),

    /** 최근 등록 순. */
    RECENTLY_ADDED(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    private final Sort sort;

    FestivalListSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
