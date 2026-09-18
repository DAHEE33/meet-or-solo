package com.survey.meetorsolo.domain.festival.dto;

/**
 * 축제 목록의 진행 상태 필터. 기간 선택({@code startDate}/{@code endDate})과 달리 "지금 기준
 * 어느 단계인가"를 고른다.
 *
 * <p><b>이 파라미터를 넘기면 목록의 가시성 규칙이 넓어진다.</b> 넘기지 않으면 기존과 같이
 * {@code ACTIVE}이면서 종료일이 지나지 않은 축제만 보이고, 넘기면 {@code ENDED}까지 포함해
 * 종료일 컷을 풀어 "진행 마감"도 검색된다. 기본값을 바꾸지 않는 이유는 같은 목록 API를
 * 홈 화면과 관광지 상세가 함께 쓰기 때문이다 — 그쪽에 종료된 축제가 섞이면 안 된다.
 *
 * <p>판정 규칙은 frontend {@code resolveDisplayStatus}와 일치시킨다. 특히 {@code ENDED}
 * 상태는 날짜와 무관하게 마감으로 본다 — 동기화가 상태를 내린 축제를 "진행 전"으로 되살리면
 * 목록과 상세 화면의 배지가 어긋난다.
 */
public enum FestivalProgressFilter {

    /** 진행 전·중·마감 전부. 탐색 화면의 기본 선택이다. */
    ALL,

    /** 아직 시작하지 않았다 — {@code eventStartDate > today}. */
    UPCOMING,

    /** 오늘 열리고 있다 — 시작했고 아직 끝나지 않았다. 날짜가 비어 있으면 열린 구간으로 본다. */
    ONGOING,

    /** 끝났다 — {@code status = ENDED}이거나 {@code eventEndDate < today}. */
    ENDED
}
