package com.survey.meetorsolo.domain.inquiry.entity;

/**
 * 스레드 발화의 작성자 구분. DB {@code chk_inquiry_messages_author_type}와 값이 일치해야 한다.
 *
 * <p>{@code author_member_id != inquiries.member_id}로 관리자를 유추하지 않고 컬럼으로 남기는
 * 이유는 관리자가 자기 문의에 답할 때 그 판정이 깨지기 때문이다
 * (docs/28_MEMBER_INQUIRY_DESIGN.md 4.3).
 */
public enum InquiryMessageAuthorType {

    USER,
    ADMIN
}
