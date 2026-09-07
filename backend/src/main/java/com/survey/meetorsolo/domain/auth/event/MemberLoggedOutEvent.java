package com.survey.meetorsolo.domain.auth.event;

/**
 * 회원이 스스로 로그아웃해 refresh token이 폐기된 사실을 알린다.
 * 관리자 제재(AdminMemberAccessRevokedEvent)와 발생 주체가 다르므로 auth 도메인에서 별도로 발행한다.
 */
public record MemberLoggedOutEvent(long memberId) {
}
