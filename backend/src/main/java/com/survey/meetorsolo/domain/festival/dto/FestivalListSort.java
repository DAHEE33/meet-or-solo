package com.survey.meetorsolo.domain.festival.dto;

/**
 * 축제 목록 정렬 기준.
 *
 * <p>"가까운순"은 제공하지 않는다 — 사용자 좌표를 서버로 보내지 않기로 했으므로 서버에는 거리
 * 정렬의 기준점이 없다(docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1).
 *
 * <p>날짜 정렬(시작일 빠른순/종료 임박순)은 제거했다. 진행 단계는 정렬이 아니라
 * {@link FestivalProgressFilter} 필터로 고르고, 기간은 직접 선택한다.
 *
 * <p><b>{@link org.springframework.data.domain.Sort}를 들고 있지 않다.</b> 좋아요·후기 정렬의
 * 정렬 키가 엔티티 속성이 아니라 집계 값이라 {@code Sort}로 표현할 수 없고, 정렬은 repository의
 * native query가 이 enum 이름을 받아 직접 수행한다.
 */
public enum FestivalListSort {

    /** 최근 등록 순. 기본값이다. */
    RECENTLY_ADDED,

    /** 좋아요(찜) 많은 순. */
    BOOKMARK_COUNT_DESC,

    /** 후기(댓글) 많은 순. */
    COMMENT_COUNT_DESC
}
