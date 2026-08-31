package com.survey.meetorsolo.global.region;

/**
 * 지역 선택 UI에 내려주는 항목. 축제와 관광지가 같은 형태를 쓴다.
 *
 * <p>{@code count}는 현재 조회 조건에서 실제로 존재하는 건수다. 데이터에 없는 시군구는 목록에
 * 아예 포함되지 않으므로, 사용자가 선택했을 때 항상 빈 결과가 나오는 지역이 노출되지 않는다.
 */
public record RegionOptionResponse(String sigunguCode, String name, long count) {
}
