package com.survey.meetorsolo.domain.content.comment.service;

import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentLikeResponse;
import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentListResponse;
import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentResponse;
import com.survey.meetorsolo.domain.content.comment.entity.ContentComment;
import com.survey.meetorsolo.domain.content.comment.entity.ContentCommentStatus;
import com.survey.meetorsolo.domain.content.comment.repository.ContentCommentLikeRepository;
import com.survey.meetorsolo.domain.content.comment.repository.ContentCommentRepository;
import com.survey.meetorsolo.domain.content.comment.repository.ContentCommentRow;
import com.survey.meetorsolo.domain.content.support.ContentTarget;
import com.survey.meetorsolo.domain.content.support.ContentTargetReader;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 댓글 등록·조회·삭제와 좋아요 토글.
 *
 * <p>정지·영구제한 회원의 신규 작성 차단은 {@code MemberAccessInterceptor}가 {@code /api/**}에서
 * 이미 처리한다. 이 서비스가 추가로 막는 것은 닉네임이 없는 {@code PROFILE_REQUIRED}
 * 회원뿐이다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 5.2).
 */
@Service
public class ContentCommentService {

    /**
     * 도배 완화 간격. 같은 회원의 마지막 댓글로부터 이 시간 안에는 새 댓글을 받지 않는다.
     *
     * <p><b>한계</b>: 동시 요청 2건은 둘 다 통과할 수 있다. 사람이 연타하는 수준만 막는 완화책이며,
     * 엄격히 막으려면 회원 단위 advisory lock이 필요해 MVP 과잉으로 제외했다(docs/27 5.2).
     */
    static final Duration MIN_COMMENT_INTERVAL = Duration.ofSeconds(5);

    private final ContentCommentRepository comments;
    private final ContentCommentLikeRepository likes;
    private final ContentTargetReader targets;
    private final MemberRepository members;
    private final Clock clock;

    public ContentCommentService(
            ContentCommentRepository comments,
            ContentCommentLikeRepository likes,
            ContentTargetReader targets,
            MemberRepository members,
            Clock clock
    ) {
        this.comments = comments;
        this.likes = likes;
        this.targets = targets;
        this.members = members;
        this.clock = clock;
    }

    /**
     * 댓글 목록. <b>비로그인에도 반드시 성공한다</b> — {@code viewerMemberId}가 {@code null}이면
     * {@code likedByMe}/{@code mine}이 모두 {@code false}인 목록을 돌려준다(docs/27 2.1, 5.5).
     */
    @Transactional(readOnly = true)
    public ContentCommentListResponse getComments(
            Long viewerMemberId,
            ContentTarget target,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ContentCommentRow> rows = target.isFestival()
                ? comments.findVisibleByFestivalId(target.id(), pageable)
                : comments.findVisibleByTourPlaceId(target.id(), pageable);

        Set<Long> likedCommentIds = likedCommentIds(viewerMemberId, rows.getContent());
        List<ContentCommentResponse> items = rows.getContent().stream()
                .map(row -> toResponse(row, viewerMemberId, likedCommentIds.contains(row.id())))
                .toList();

        return new ContentCommentListResponse(
                items,
                rows.getNumber(),
                rows.getSize(),
                rows.getTotalElements(),
                rows.getTotalPages(),
                rows.hasNext()
        );
    }

    /**
     * 내가 좋아요한 댓글 id를 한 번의 쿼리로 모은다. 목록 크기와 무관하게 쿼리 1건이라 N+1이
     * 생기지 않는다(docs/27 5.5).
     */
    private Set<Long> likedCommentIds(Long viewerMemberId, List<ContentCommentRow> rows) {
        if (viewerMemberId == null || rows.isEmpty()) {
            return Set.of();
        }
        List<Long> commentIds = rows.stream().map(ContentCommentRow::id).toList();
        return Set.copyOf(likes.findLikedCommentIds(viewerMemberId, commentIds));
    }

    private ContentCommentResponse toResponse(ContentCommentRow row, Long viewerMemberId, boolean likedByMe) {
        return new ContentCommentResponse(
                row.id(),
                row.nickname(),
                row.body(),
                row.likeCount(),
                likedByMe,
                viewerMemberId != null && viewerMemberId.equals(row.authorMemberId()),
                row.createdAt()
        );
    }

    @Transactional
    public ContentCommentResponse create(long memberId, ContentTarget target, String rawBody) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        // 닉네임이 없으면 댓글에 표시할 이름이 없다.
        if (Member.STATUS_PROFILE_REQUIRED.equals(member.getStatus())
                || member.getNickname() == null || member.getNickname().isBlank()) {
            throw new BusinessException(ErrorCode.CONTENT_COMMENT_PROFILE_REQUIRED);
        }

        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty() || body.length() > ContentComment.BODY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.CONTENT_COMMENT_INVALID_REQUEST);
        }

        targets.requireVisible(target);

        OffsetDateTime now = OffsetDateTime.now(clock);
        requireNotTooFrequent(memberId, now);

        ContentComment saved = comments.save(ContentComment.create(memberId, target, body));
        return new ContentCommentResponse(
                saved.getId(),
                member.getNickname(),
                saved.getBody(),
                0,
                false,
                true,
                saved.getCreatedAt()
        );
    }

    private void requireNotTooFrequent(long memberId, OffsetDateTime now) {
        Optional<OffsetDateTime> latest = comments.findLatestCreatedAtByMemberId(memberId);
        if (latest.isEmpty()) {
            return;
        }
        OffsetDateTime allowedFrom = latest.get().plus(MIN_COMMENT_INTERVAL);
        if (now.isBefore(allowedFrom)) {
            throw new BusinessException(ErrorCode.CONTENT_COMMENT_TOO_FREQUENT);
        }
    }

    /**
     * 작성자 본인 삭제. 이미 삭제된 댓글에 다시 호출해도 성공한다(멱등).
     * 남의 댓글이면 {@code FORBIDDEN}, 아예 없으면 {@code NOT_FOUND}다(docs/27 5.3).
     */
    @Transactional
    public void delete(long memberId, long commentId) {
        int affected = comments.softDeleteByAuthor(commentId, memberId, OffsetDateTime.now(clock));
        if (affected > 0) {
            return;
        }
        // 전환된 행이 없으면 "이미 삭제됨"과 "남의 댓글"과 "없는 댓글"을 구분해야 한다.
        ContentComment comment = comments.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_COMMENT_NOT_FOUND));
        if (!comment.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.CONTENT_COMMENT_FORBIDDEN);
        }
        // 본인 댓글인데 VISIBLE이 아니었다 = 이미 삭제·숨김 상태. 멱등하게 성공 처리한다.
    }

    /**
     * 좋아요 토글. <b>{@code content_comment_likes}의 실제 영향 행 수가 1일 때만 카운터를
     * 움직인다</b> — 이것이 멱등성과 카운터 정합성을 동시에 만족시키는 지점이다(docs/27 2.2).
     */
    @Transactional
    public ContentCommentLikeResponse toggleLike(long memberId, long commentId, boolean liked) {
        ContentComment comment = comments.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_COMMENT_NOT_FOUND));
        if (comment.getStatus() != ContentCommentStatus.VISIBLE) {
            throw new BusinessException(ErrorCode.CONTENT_COMMENT_NOT_FOUND);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (liked) {
            // 이미 눌렀거나 동시 요청이 겹치면 0이 돌아오고 카운터는 그대로다.
            if (likes.insertIgnoringConflict(commentId, memberId, now) > 0) {
                comments.increaseLikeCount(commentId, now);
            }
        } else if (likes.deleteByCommentIdAndMemberId(commentId, memberId) > 0) {
            comments.decreaseLikeCount(commentId, now);
        }

        int likeCount = comments.findLikeCountById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_COMMENT_NOT_FOUND));
        return new ContentCommentLikeResponse(liked, likeCount);
    }

    /**
     * 대상의 공개 댓글 수. engagement 응답의 {@code commentCount}가 쓴다.
     * 비로그인 호출 경로에도 쓰이므로 인증을 요구하지 않는다.
     */
    @Transactional(readOnly = true)
    public long countVisibleComments(ContentTarget target) {
        return target.isFestival()
                ? comments.countVisibleByFestivalId(target.id())
                : comments.countVisibleByTourPlaceId(target.id());
    }

    /**
     * 탈퇴 시 해당 회원의 공개 댓글을 일괄 숨긴다(docs/27 5.7).
     *
     * <p>좋아요 row는 남긴다 — 댓글이 목록에서 빠지므로 노출되지 않고, FK가
     * {@code ON DELETE RESTRICT}라 물리 삭제가 애초에 막혀 있다.
     *
     * <p>{@code MemberWithdrawalService}가 탈퇴 transaction 안에서
     * {@code ContentBookmarkService.deleteAllOnWithdrawal}과 함께 호출한다(docs/19 4.4).
     *
     * @return 숨김으로 전환된 댓글 수
     */
    @Transactional
    public int softDeleteAllOnWithdrawal(long memberId) {
        return comments.softDeleteAllByMemberId(memberId, OffsetDateTime.now(clock));
    }
}
