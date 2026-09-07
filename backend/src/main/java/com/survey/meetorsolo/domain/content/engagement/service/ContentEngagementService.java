package com.survey.meetorsolo.domain.content.engagement.service;

import com.survey.meetorsolo.domain.content.bookmark.service.ContentBookmarkService;
import com.survey.meetorsolo.domain.content.comment.service.ContentCommentService;
import com.survey.meetorsolo.domain.content.engagement.dto.ContentEngagementResponse;
import com.survey.meetorsolo.domain.content.engagement.dto.ContentEngagementViewerResponse;
import com.survey.meetorsolo.domain.content.support.ContentTarget;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상세 화면 진입용 찜 상태 + 댓글 수 + 열람자 정보를 한 번에 만든다.
 *
 * <p>이 서비스는 <b>절대 인증 예외를 던지지 않는다.</b> 비로그인 열람자는
 * {@code bookmarked = false}, {@code viewer.loggedIn = false}로 정상 응답한다
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.1).
 */
@Service
public class ContentEngagementService {

    private final ContentBookmarkService bookmarks;
    private final ContentCommentService comments;
    private final MemberRepository members;

    public ContentEngagementService(
            ContentBookmarkService bookmarks,
            ContentCommentService comments,
            MemberRepository members
    ) {
        this.bookmarks = bookmarks;
        this.comments = comments;
        this.members = members;
    }

    /**
     * @param viewerMemberId 유효한 access token의 회원 id. 비로그인·만료·위조는 모두 {@code null}
     */
    @Transactional(readOnly = true)
    public ContentEngagementResponse getEngagement(Long viewerMemberId, ContentTarget target) {
        return new ContentEngagementResponse(
                bookmarks.isBookmarked(viewerMemberId, target),
                comments.countVisibleComments(target),
                resolveViewer(viewerMemberId)
        );
    }

    /**
     * 관리자 여부는 공개 댓글 섹션에서 관리자에게만 숨김 버튼을 노출하기 위해 함께 내려준다.
     * 회원 row가 사라진 토큰이면 로그인하지 않은 것으로 본다.
     */
    private ContentEngagementViewerResponse resolveViewer(Long viewerMemberId) {
        if (viewerMemberId == null) {
            return new ContentEngagementViewerResponse(false, false);
        }
        return members.findById(viewerMemberId)
                .map(member -> new ContentEngagementViewerResponse(
                        true,
                        Member.ROLE_ADMIN.equals(member.getRole())
                ))
                .orElseGet(() -> new ContentEngagementViewerResponse(false, false));
    }
}
