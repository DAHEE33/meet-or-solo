package com.survey.meetorsolo.domain.content.engagement.dto;

/**
 * 이 응답을 받는 사람이 로그인했는지, 관리자인지.
 *
 * <p>{@code admin}은 공개 댓글 섹션에서 관리자에게만 숨김 버튼을 노출하기 위한 값이다. 이 값을
 * engagement 응답에 함께 담아 관리자 판별용 추가 요청을 없앤다(docs/27 2.3).
 *
 * <p>{@code canComment}는 이 열람자가 댓글을 쓸 수 있는지다. 축제 댓글은 그 축제에 체크인한
 * 적이 있어야 쓸 수 있으므로(docs/27 5.2), 화면이 입력창 대신 안내를 띄울지 판단하려면 이 값이
 * 필요하다. 관광지에는 체크인이 없어 로그인 여부와 같다.
 *
 * <p>회원 상태·권한 문자열 같은 내부 값은 담지 않는다(docs/27 6.3).
 */
public record ContentEngagementViewerResponse(boolean loggedIn, boolean admin, boolean canComment) {
}
