package com.survey.meetorsolo.domain.content.bookmark.entity;

import com.survey.meetorsolo.domain.content.support.ContentTarget;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 축제 또는 관광지 찜 1건. {@code festivalId}와 {@code tourPlaceId} 중 정확히 하나만 값을 가지며
 * DB의 {@code chk_content_bookmarks_target}이 이를 강제한다.
 *
 * <p>해제는 soft delete가 아니라 물리 삭제다({@code user_blocks}와 같은 구조).
 * 실제 등록·해제는 멱등성 때문에 {@code ON CONFLICT DO NOTHING} native query로 수행하므로
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 5.1) 이 엔티티는 스키마 정의와 조회에 쓰인다.
 */
@Entity
@Table(name = "content_bookmarks")
public class ContentBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "festival_id")
    private Long festivalId;

    @Column(name = "tour_place_id")
    private Long tourPlaceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ContentBookmark() {
    }

    public static ContentBookmark create(Long memberId, ContentTarget target) {
        Objects.requireNonNull(target, "target");
        ContentBookmark bookmark = new ContentBookmark();
        bookmark.memberId = Objects.requireNonNull(memberId, "memberId");
        bookmark.festivalId = target.festivalId();
        bookmark.tourPlaceId = target.tourPlaceId();
        return bookmark;
    }

    @PrePersist
    void prePersist() {
        createdAt = SeoulDateTime.now();
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
