package com.survey.meetorsolo.domain.inquiry.entity;

/**
 * 문의 상태. DB {@code chk_inquiries_status}와 값이 일치해야 한다.
 *
 * <p>전이 규칙은 docs/28_MEMBER_INQUIRY_DESIGN.md 5.8절이다. {@link #CLOSED}는 종단이며
 * 재개하려면 새 문의를 등록한다.
 */
public enum InquiryStatus {

    /** 접수. 관리자가 아직 열지 않았다. */
    RECEIVED,
    /** 확인 중. 관리자가 열었거나, 답변 후 사용자가 추가 질문을 남겼다. */
    IN_PROGRESS,
    /** 답변 완료 */
    ANSWERED,
    /** 종결. 추가 질문과 답변을 모두 받지 않는다. */
    CLOSED;

    /** 관리자 미처리 목록에 뜨는 상태인지. 미답변 건수 제한도 이 판정을 쓴다(docs/28 5.1). */
    public boolean isOpen() {
        return this == RECEIVED || this == IN_PROGRESS;
    }
}
