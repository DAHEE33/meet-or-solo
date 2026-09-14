package com.survey.meetorsolo.domain.content.support;

/**
 * 대상 1건의 집계 수. 목록 여러 건의 찜 수·댓글 수를 <b>한 번의 쿼리로</b> 모으기 위한 조회
 * 결과 타입이다(항목마다 조회하면 N+1이 된다).
 *
 * <p>{@code count}가 {@code Long}인 이유는 JPQL {@code count(...)}의 결과 타입이
 * {@code Long}이라 생성자 표현식이 그대로 받아야 하기 때문이다.
 */
public record ContentTargetCountRow(Long targetId, Long count) {
}
