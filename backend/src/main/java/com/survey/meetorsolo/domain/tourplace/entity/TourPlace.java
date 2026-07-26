package com.survey.meetorsolo.domain.tourplace.entity;

import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "tour_places",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tour_places_content_id", columnNames = "content_id")
        }
)
public class TourPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false, length = 50)
    private String contentId;

    @Column(name = "content_type_id", nullable = false, length = 20)
    private String contentTypeId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 500)
    private String address;

    @Column(name = "map_x", precision = 13, scale = 10)
    private BigDecimal mapX;

    @Column(name = "map_y", precision = 13, scale = 10)
    private BigDecimal mapY;

    @Column(length = 100)
    private String tel;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TourPlaceStatus status = TourPlaceStatus.ACTIVE;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TourPlace() {
    }

    public static TourPlace create(TourPlaceSyncData data) {
        TourPlace place = new TourPlace();
        place.contentId = data.contentId();
        place.applySyncData(data);
        return place;
    }

    public void synchronize(TourPlaceSyncData data) {
        if (!contentId.equals(data.contentId())) {
            throw new IllegalArgumentException("기존 관광지의 contentId는 변경할 수 없습니다.");
        }
        applySyncData(data);
    }

    private void applySyncData(TourPlaceSyncData data) {
        this.contentTypeId = data.contentTypeId();
        this.title = data.title();
        this.address = data.address();
        this.mapX = data.mapX();
        this.mapY = data.mapY();
        this.tel = data.tel();
        this.imageUrl = data.imageUrl();
        this.lastSyncedAt = data.syncedAt();
        this.rawData = new LinkedHashMap<>(data.rawData());
        if (status != TourPlaceStatus.HIDDEN) {
            this.status = TourPlaceStatus.ACTIVE;
        }
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = SeoulDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = SeoulDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getContentId() {
        return contentId;
    }

    public String getContentTypeId() {
        return contentTypeId;
    }

    public String getTitle() {
        return title;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getMapX() {
        return mapX;
    }

    public BigDecimal getMapY() {
        return mapY;
    }

    public String getTel() {
        return tel;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public TourPlaceStatus getStatus() {
        return status;
    }

    public OffsetDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public Map<String, Object> getRawData() {
        return rawData == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rawData));
    }
}
