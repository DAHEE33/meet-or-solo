package com.survey.meetorsolo.domain.content.comment.repository;

import com.survey.meetorsolo.domain.content.comment.entity.ContentCommentLike;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentCommentLikeRepository extends JpaRepository<ContentCommentLike, Long> {

    /**
     * 좋아요 등록. 이미 눌렀거나 동시 요청이 겹치면 {@code uq_content_comment_likes_pair}가
     * 잡아내고 {@code ON CONFLICT DO NOTHING}이 예외 없이 0을 반환한다.
     *
     * <p><b>반환값이 카운터를 움직일지 결정한다.</b> 1이면 호출부가
     * {@code ContentCommentRepository.increaseLikeCount}를 부르고, 0이면 부르지 않는다
     * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.2).
     *
     * @return 실제로 삽입된 행 수(0 또는 1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into content_comment_likes (comment_id, member_id, created_at)
            values (:commentId, :memberId, :createdAt)
            on conflict (comment_id, member_id) do nothing
            """, nativeQuery = true)
    int insertIgnoringConflict(
            @Param("commentId") long commentId,
            @Param("memberId") long memberId,
            @Param("createdAt") OffsetDateTime createdAt
    );

    /**
     * 좋아요 해제. 누른 적이 없으면 0을 반환하고 호출부는 카운터를 건드리지 않는다.
     *
     * @return 실제로 삭제된 행 수(0 또는 1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from content_comment_likes
            where comment_id = :commentId and member_id = :memberId
            """, nativeQuery = true)
    int deleteByCommentIdAndMemberId(
            @Param("commentId") long commentId,
            @Param("memberId") long memberId
    );

    /**
     * 목록 화면의 {@code likedByMe}용. 댓글 id 목록을 한 번에 조회해 N+1을 만들지 않는다
     * (docs/27 5.5).
     */
    @Query("""
            select commentLike.commentId
            from ContentCommentLike commentLike
            where commentLike.memberId = :memberId
              and commentLike.commentId in :commentIds
            """)
    List<Long> findLikedCommentIds(
            @Param("memberId") long memberId,
            @Param("commentIds") Collection<Long> commentIds
    );

    boolean existsByCommentIdAndMemberId(long commentId, long memberId);
}
