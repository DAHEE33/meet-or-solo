package com.survey.meetorsolo.domain.tourplace.dto;

import org.springframework.data.domain.Sort;

/**
 * 관광지 목록 정렬 기준. "가까운순"/"먼순"은 제공하지 않는다 — 사용자 좌표를 서버로 보내지
 * 않기로 했으므로 서버에는 거리 정렬의 기준점이 없다
 * (docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1, 6.2).
 *
 * <p>"내 주변" 성격의 조회는 축제 좌표를 중심으로 하는 기존
 * {@code GET /api/festivals/{id}/nearby-spots}가 담당한다(좌표 전송이 없다).
 */
public enum TourPlaceListSort {

    /** 제목 오름차순. 기존 동작과 동일한 기본값이다. */
    TITLE_ASC(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"))),

    /** 최근 등록 순. */
    RECENTLY_ADDED(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    private final Sort sort;

    TourPlaceListSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
