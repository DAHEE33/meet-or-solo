package com.survey.meetorsolo.domain.inquiry.admin.dto;

/**
 * 문의 작성자 요약. 제재 이의제기 판단에 회원 상태가 필요하다.
 * 노출 범위는 {@code AdminReportMemberSummaryResponse}와 같다(docs/28 7절).
 */
public record AdminInquiryMemberSummaryResponse(
        long memberId,
        String nickname,
        String status
) {
}
