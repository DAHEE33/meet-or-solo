package com.survey.meetorsolo.domain.tourplace.dto;

/**
 * 관광지 목록 정렬 기준. "가까운순"/"먼순"은 제공하지 않는다 — 사용자 좌표를 서버로 보내지
 * 않기로 했으므로 서버에는 거리 정렬의 기준점이 없다
 * (docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 3.1, 6.2).
 *
 * <p>"내 주변" 성격의 조회는 축제 좌표를 중심으로 하는 기존
 * {@code GET /api/festivals/{id}/nearby-spots}가 담당한다(좌표 전송이 없다).
 *
 * <p>축제 쪽과 달리 이름순을 남긴다 — 관광지에는 기간이 없어 이름순이 유일한 안정적 기본값이다.
 * {@link org.springframework.data.domain.Sort}를 들고 있지 않은 이유는
 * {@code FestivalListSort}와 같다(집계 값 정렬은 Sort로 표현할 수 없다).
 */
public enum TourPlaceListSort {

    /** 제목 오름차순. 기존 동작과 동일한 기본값이다. */
    TITLE_ASC,

    /** 최근 등록 순. */
    RECENTLY_ADDED,

    /** 좋아요(찜) 많은 순. */
    BOOKMARK_COUNT_DESC,

    /** 후기(댓글) 많은 순. */
    COMMENT_COUNT_DESC
}
