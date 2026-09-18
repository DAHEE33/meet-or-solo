package com.survey.meetorsolo.domain.festival.entity;

import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "festivals",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_festivals_content_id", columnNames = "content_id")
        }
)
public class Festival {

    private static final int DEFAULT_CHECKIN_RADIUS_METERS = 500;
    private static final int DEFAULT_MEETING_RADIUS_METERS = 2000;

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

    @Column(name = "area_code", length = 20)
    private String areaCode;

    @Column(name = "sigungu_code", length = 20)
    private String sigunguCode;

    @Column(name = "event_start_date")
    private LocalDate eventStartDate;

    @Column(name = "event_end_date")
    private LocalDate eventEndDate;

    @Column(name = "map_x", precision = 13, scale = 10)
    private BigDecimal mapX;

    @Column(name = "map_y", precision = 13, scale = 10)
    private BigDecimal mapY;

    @Column(name = "checkin_radius_meters", nullable = false)
    private Integer checkinRadiusMeters = DEFAULT_CHECKIN_RADIUS_METERS;

    @Column(name = "meeting_radius_meters", nullable = false)
    private Integer meetingRadiusMeters = DEFAULT_MEETING_RADIUS_METERS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FestivalStatus status = FestivalStatus.ACTIVE;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Festival() {
    }

    public static Festival create(FestivalSyncData data, LocalDate syncDate) {
        Festival festival = new Festival();
        festival.contentId = data.contentId();
        festival.applySyncData(data, syncDate);
        return festival;
    }

    public void synchronize(FestivalSyncData data, LocalDate syncDate) {
        if (!contentId.equals(data.contentId())) {
            throw new IllegalArgumentException("기존 축제의 contentId는 변경할 수 없습니다.");
        }
        applySyncData(data, syncDate);
    }

    private void applySyncData(FestivalSyncData data, LocalDate syncDate) {
        this.contentTypeId = data.contentTypeId();
        this.title = data.title();
        this.address = data.address();
        this.areaCode = data.regionCode();
        this.sigunguCode = data.sigunguCode();
        this.eventStartDate = data.eventStartDate();
        this.eventEndDate = data.eventEndDate();
        this.mapX = data.mapX();
        this.mapY = data.mapY();
        this.lastSyncedAt = data.syncedAt();
        this.rawData = new LinkedHashMap<>(data.rawData());
        if (status != FestivalStatus.HIDDEN) {
            this.status = resolveStatus(syncDate, eventEndDate);
        }
    }

    private FestivalStatus resolveStatus(LocalDate syncDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(syncDate)) {
            return FestivalStatus.ENDED;
        }
        return FestivalStatus.ACTIVE;
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

    public String getAreaCode() {
        return areaCode;
    }

    public String getSigunguCode() {
        return sigunguCode;
    }

    public LocalDate getEventStartDate() {
        return eventStartDate;
    }

    public LocalDate getEventEndDate() {
        return eventEndDate;
    }

    public BigDecimal getMapX() {
        return mapX;
    }

    public BigDecimal getMapY() {
        return mapY;
    }

    public Integer getCheckinRadiusMeters() {
        return checkinRadiusMeters;
    }

    public Integer getMeetingRadiusMeters() {
        return meetingRadiusMeters;
    }

    public FestivalStatus getStatus() {
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
