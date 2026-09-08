package com.survey.meetorsolo.domain.content.engagement.dto;

/**
 * 이 응답을 받는 사람이 로그인했는지, 관리자인지.
 *
 * <p>{@code admin}은 공개 댓글 섹션에서 관리자에게만 숨김 버튼을 노출하기 위한 값이다. 이 값을
 * engagement 응답에 함께 담아 관리자 판별용 추가 요청을 없앤다(docs/27 2.3).
 *
 * <p>회원 상태·권한 문자열 같은 내부 값은 담지 않는다(docs/27 6.3).
 */
public record ContentEngagementViewerResponse(boolean loggedIn, boolean admin) {
}
