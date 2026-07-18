package com.survey.meetorsolo.domain.festival.entity;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "festival_images")
public class FestivalImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festival festival;

    @Column(name = "origin_image_url", nullable = false, length = 1000)
    private String originImageUrl;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FestivalImage() {
    }

    public static FestivalImage representative(
            Festival festival,
            String originImageUrl,
            String thumbnailUrl
    ) {
        FestivalImage image = new FestivalImage();
        image.festival = Objects.requireNonNull(festival, "festival");
        image.updateRepresentative(originImageUrl, thumbnailUrl);
        return image;
    }

    public void updateRepresentative(String originImageUrl, String thumbnailUrl) {
        this.originImageUrl = requiredUrl(originImageUrl, "originImageUrl");
        this.thumbnailUrl = optionalUrl(thumbnailUrl, "thumbnailUrl");
        this.displayOrder = 0;
    }

    private String requiredUrl(String value, String fieldName) {
        String normalized = optionalUrl(value, fieldName);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return normalized;
    }

    private String optionalUrl(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException(fieldName + "은 1000자를 초과할 수 없습니다.");
        }
        return normalized;
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

    public Long getFestivalId() {
        return festival.getId();
    }

    public String getOriginImageUrl() {
        return originImageUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
