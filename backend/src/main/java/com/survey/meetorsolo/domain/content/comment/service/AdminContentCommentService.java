package com.survey.meetorsolo.domain.content.comment.service;

import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.content.comment.dto.AdminContentCommentVisibilityResponse;
import com.survey.meetorsolo.domain.content.comment.repository.ContentCommentRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 댓글 숨김·재공개. 별도 관리 화면 없이 공개 댓글 섹션에서 관리자에게만 노출되는
 * 숨김 버튼이 호출한다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.3, 5.6).
 *
 * <p>댓글 신고는 이번 범위에서 제외했다 — {@code reports}에 target 개념이 없어 확장하면 V25의
 * 신고 누적 자동 제재 파이프라인까지 건드려야 한다. 그래서 최소 모더레이션 수단으로 이 API만 둔다.
 */
@Service
public class AdminContentCommentService {

    private final AdminAuthorizationService authorization;
    private final ContentCommentRepository comments;
    private final Clock clock;

    public AdminContentCommentService(
            AdminAuthorizationService authorization,
            ContentCommentRepository comments,
            Clock clock
    ) {
        this.authorization = authorization;
        this.comments = comments;
        this.clock = clock;
    }

    /**
     * @param visible {@code false}면 숨김, {@code true}면 재공개
     */
    @Transactional
    public AdminContentCommentVisibilityResponse changeVisibility(
            long adminMemberId,
            long commentId,
            boolean visible
    ) {
        authorization.requireAdmin(adminMemberId);

        if (!comments.existsById(commentId)) {
            throw new BusinessException(ErrorCode.CONTENT_COMMENT_NOT_FOUND);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        // 재공개는 HIDDEN만, 숨김은 VISIBLE만 전환한다. 작성자가 삭제한 DELETED는 어느 쪽도
        // 건드리지 않으므로 affected가 0이 되고 changed = false로 응답한다.
        int affected = visible
                ? comments.showByAdmin(commentId, now)
                : comments.hideByAdmin(commentId, now);

        return new AdminContentCommentVisibilityResponse(visible, affected > 0);
    }
}
