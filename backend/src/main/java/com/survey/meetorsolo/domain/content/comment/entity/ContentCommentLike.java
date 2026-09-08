package com.survey.meetorsolo.domain.content.comment.entity;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 댓글 좋아요 1건. {@code uq_content_comment_likes_pair}가 멱등성의 근원이며, 연타·동시 요청은
 * {@code ON CONFLICT DO NOTHING}으로 흡수된다
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.2).
 *
 * <p>자기 댓글 좋아요는 허용한다 — {@code member_reviews}의 self CHECK와 달리 막을 실익이 없다.
 *
 * <p>등록·해제는 카운터 정합성 때문에 native query로 수행하므로 이 엔티티는 스키마 정의와
 * "내가 좋아요한 댓글 id" 조회에 쓰인다.
 */
@Entity
@Table(
        name = "content_comment_likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_content_comment_likes_pair",
                columnNames = {"comment_id", "member_id"}
        )
)
public class ContentCommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ContentCommentLike() {
    }

    public static ContentCommentLike create(Long commentId, Long memberId) {
        ContentCommentLike like = new ContentCommentLike();
        like.commentId = Objects.requireNonNull(commentId, "commentId");
        like.memberId = Objects.requireNonNull(memberId, "memberId");
        return like;
    }

    @PrePersist
    void prePersist() {
        createdAt = SeoulDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
