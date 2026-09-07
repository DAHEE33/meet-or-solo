package com.survey.meetorsolo.domain.content.comment.dto;

import java.time.OffsetDateTime;

/**
 * 공개 댓글 1건.
 *
 * <p>{@code memberId}와 {@code profileImageUrl}을 담지 않는다. 차단·신고 연동이 없어 내부 ID가
 * 필요 없고, 프로필 이미지는 비로그인 포함 전체 공개 화면에 노출할 대상이 아니다
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 6.2, 6.3). 화면은 {@code nickname} 첫 글자로
 * 이니셜 아바타를 그리고, {@code mine}으로 삭제 버튼 노출을 판단한다.
 */
public record ContentCommentResponse(
        Long id,
        String nickname,
        String body,
        int likeCount,
        boolean likedByMe,
        boolean mine,
        OffsetDateTime createdAt
) {
}
