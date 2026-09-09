package com.survey.meetorsolo.domain.inquiry.entity;

/**
 * 문의 우선순위. DB {@code chk_inquiries_priority}와 값이 일치해야 한다.
 *
 * <p><b>사용자는 이 값을 지정할 수 없다.</b> 사용자가 고르게 하면 사실상 모든 문의가
 * {@link #URGENT}로 들어와 우선순위가 무의미해진다(docs/28_MEMBER_INQUIRY_DESIGN.md 확정 5번).
 * 등록 요청은 이 필드를 받지 않고 관리자 {@code PATCH}만 변경한다.
 */
public enum InquiryPriority {

    NORMAL,
    URGENT
}
