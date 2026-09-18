/**
 * 1:1 문의 센터. 사용자가 프로필에서 문의를 남기고 관리자가 별도 메뉴에서 답변한다.
 *
 * <p>설계와 확정 사항은 {@code docs/29_MEMBER_INQUIRY_DESIGN.md}, 로드맵 위치는
 * {@code docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md} 4.5절이다.
 *
 * <p>이 기능의 1순위 용도는 제재 이의제기다. 다만 영구제한({@code BANNED}) 회원은
 * {@code MemberAccessInterceptor}에 막혀 이 API에 도달하지 못하므로, 그 경로는 고객센터 이메일
 * 안내를 유지한다(docs/29 2.1).
 */
package com.survey.meetorsolo.domain.inquiry;
