package com.survey.meetorsolo.domain.content.comment.dto;

import java.time.OffsetDateTime;

/**
 * 공개 댓글 1건.
 *
 * <p>{@code memberId}를 담지 않는다. 차단·신고 연동이 없어 내부 ID가 필요 없다
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 6.3). {@code mine}으로 삭제 버튼 노출을 판단한다.
 *
 * <p><b>{@code profileImageUrl}은 로그인한 회원에게만 담는다.</b> 예전에는 아무에게도 담지
 * 않아서, 프로필 사진을 등록해도 댓글에는 닉네임 이니셜만 보였다. 비로그인에게는 여전히
 * {@code null}이다 — 얼굴 사진과 소셜 프로필 사진을 비회원·크롤러에게까지 열지는 않는다
 * (docs/27 6.2). 값이 {@code null}이면 화면은 이니셜 아바타를 그린다.
 */
public record ContentCommentResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        String body,
        int likeCount,
        boolean likedByMe,
        boolean mine,
        OffsetDateTime createdAt
) {
}
