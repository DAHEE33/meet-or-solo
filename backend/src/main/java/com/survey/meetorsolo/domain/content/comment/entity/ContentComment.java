package com.survey.meetorsolo.domain.content.comment.entity;

import com.survey.meetorsolo.domain.content.support.ContentTarget;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 축제 또는 관광지에 달린 공개 댓글 1건.
 *
 * <p>공개 콘텐츠이므로 본문은 암호화하지 않는다 — {@code member_reviews.comment_encrypted}와
 * 반대인 이유는 그쪽이 비공개 상호 평가라서다
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 6.1).
 *
 * <p>{@code likeCount}는 비정규화 카운터다. {@code content_comment_likes}의 실제 영향 행 수가
 * 1일 때만 움직이며 증감은 {@code like_count = like_count ± 1} native query로 수행한다
 * (docs/27 2.2). 그래서 이 엔티티에는 카운터를 직접 바꾸는 메서드를 두지 않는다.
 */
@Entity
@Table(name = "content_comments")
public class ContentComment {

    public static final int BODY_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "festival_id")
    private Long festivalId;

    @Column(name = "tour_place_id")
    private Long tourPlaceId;

    @Column(nullable = false, length = BODY_MAX_LENGTH)
    private String body;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentCommentStatus status = ContentCommentStatus.VISIBLE;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ContentComment() {
    }

    /**
     * @param body 호출부가 이미 trim·길이 검증을 마친 본문
     */
    public static ContentComment create(Long memberId, ContentTarget target, String body) {
        Objects.requireNonNull(target, "target");
        String normalizedBody = Objects.requireNonNull(body, "body").trim();
        if (normalizedBody.isEmpty() || normalizedBody.length() > BODY_MAX_LENGTH) {
            throw new IllegalArgumentException("body는 1~" + BODY_MAX_LENGTH + "자여야 합니다.");
        }
        ContentComment comment = new ContentComment();
        comment.memberId = Objects.requireNonNull(memberId, "memberId");
        comment.festivalId = target.festivalId();
        comment.tourPlaceId = target.tourPlaceId();
        comment.body = normalizedBody;
        comment.likeCount = 0;
        comment.status = ContentCommentStatus.VISIBLE;
        return comment;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = SeoulDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = SeoulDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getFestivalId() {
        return festivalId;
    }

    public Long getTourPlaceId() {
        return tourPlaceId;
    }

    public String getBody() {
        return body;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public ContentCommentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
