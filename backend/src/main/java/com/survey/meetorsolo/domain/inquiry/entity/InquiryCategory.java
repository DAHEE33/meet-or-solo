package com.survey.meetorsolo.domain.inquiry.entity;

/**
 * 문의 분류. DB {@code chk_inquiries_category}와 값이 일치해야 한다.
 *
 * <p>안전(신고 성격) 분류를 두지 않는다. 신고자 보호 제약과 충돌하고 구조화 신고 경로가 이미
 * 있기 때문이다(docs/29_MEMBER_INQUIRY_DESIGN.md 3.4).
 */
public enum InquiryCategory {

    /** 제재 이의제기. 이 기능의 1순위 용도다(docs/29 1절). */
    SANCTION_APPEAL,
    /** 계정·로그인 */
    ACCOUNT,
    /** 동행 매칭 */
    MATCHING,
    /** 축제·관광지 정보 오류 */
    FESTIVAL_DATA,
    /** 오류 제보 */
    BUG,
    /** 기타 */
    ETC
}
