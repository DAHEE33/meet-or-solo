package com.survey.meetorsolo.domain.inquiry.admin.dto;

import com.survey.meetorsolo.domain.inquiry.entity.InquiryPriority;

/**
 * 관리자 문의 상태·우선순위 변경. 둘 다 optional이며 적어도 하나는 있어야 한다.
 *
 * <p>긴급 지정이 관리자 전용이라 상태 변경과 같은 endpoint에서 함께 받는다. 사용자 요청에는
 * {@code priority} 필드가 없다(docs/29 확정 5번).
 */
public record AdminInquiryUpdateRequest(
        AdminInquiryTargetStatus status,
        InquiryPriority priority
) {
}
