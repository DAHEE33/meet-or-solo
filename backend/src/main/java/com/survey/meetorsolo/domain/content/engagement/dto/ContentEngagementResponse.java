package com.survey.meetorsolo.domain.content.engagement.dto;

/**
 * 상세 화면 진입 시 한 번에 받아가는 찜 상태와 댓글 수.
 *
 * <p>비로그인 사용자에게도 반드시 {@code 200}으로 응답한다. frontend {@code apiClient}가 모든
 * {@code 401}을 {@code /login} 전역 리다이렉트로 처리하기 때문이다
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.1).
 *
 * <p>{@code viewer.loggedIn}이 있는 이유도 같다 — 화면이 로그인 여부를 알기 위해
 * {@code GET /api/members/me}를 호출하면 비로그인 사용자가 튕긴다. 그래서 이 공개 응답이
 * 로그인 여부를 알려준다.
 *
 * <p>찜 공개 카운트({@code N명이 찜했어요})는 이번 범위에서 제외했다 — 개인 상태만 준다(docs/27 9).
 */
public record ContentEngagementResponse(
        boolean bookmarked,
        long commentCount,
        ContentEngagementViewerResponse viewer
) {
}
