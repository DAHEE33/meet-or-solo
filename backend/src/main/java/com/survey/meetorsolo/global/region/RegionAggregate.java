package com.survey.meetorsolo.global.region;

/**
 * 지역(시군구) 목록 집계용 repository 프로젝션.
 *
 * <p>시군구 <b>코드</b>는 컬럼으로 있지만 시군구 <b>이름</b>은 어디에도 저장돼 있지 않다
 * (TourAPI 원본 raw_data에도 이름 필드가 없다). 대신 주소 문자열의 두 번째 토큰이
 * "강릉시", "평창군"처럼 일관되게 시군구명이므로, 그룹별 대표 주소를 하나 가져와
 * {@link RegionNameResolver}가 이름을 뽑는다. 자세한 배경은
 * docs/25_FESTIVAL_TOURPLACE_LIST_FILTER_DESIGN.md 2.2와 5.1 참고.
 */
public record RegionAggregate(String sigunguCode, String sampleAddress, Long count) {
}
