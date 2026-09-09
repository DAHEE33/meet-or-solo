package com.survey.meetorsolo.domain.inquiry.dto;

/**
 * 미확인 답변 수. {@code MyPage} 진입점 badge가 목록 전체를 불러오지 않고 이 값만 읽는다.
 *
 * <p>관리자 답변을 밀어줄 채널이 없어 이 badge가 유일한 도달 신호다(docs/28 2.2).
 */
public record InquiryUnreadCountResponse(long count) {
}
