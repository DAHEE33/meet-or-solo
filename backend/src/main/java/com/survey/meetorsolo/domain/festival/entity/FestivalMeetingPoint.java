package com.survey.meetorsolo.domain.festival.entity;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "festival_meeting_points")
public class FestivalMeetingPoint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "festival_id", nullable = false) private Long festivalId;
    @Column(name = "kakao_place_id", nullable = false, length = 50) private String kakaoPlaceId;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 500) private String address;
    @Column(name = "map_x", nullable = false, precision = 13, scale = 10) private BigDecimal mapX;
    @Column(name = "map_y", nullable = false, precision = 13, scale = 10) private BigDecimal mapY;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FestivalMeetingPointStatus status;
    @Column(name = "assignment_order", nullable = false) private Integer assignmentOrder;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected FestivalMeetingPoint() {
    }

    public static FestivalMeetingPoint inactive(long festivalId, String kakaoPlaceId, String name,
            String address, BigDecimal mapX, BigDecimal mapY, int assignmentOrder) {
        FestivalMeetingPoint point = new FestivalMeetingPoint();
        point.festivalId = festivalId;
        point.status = FestivalMeetingPointStatus.INACTIVE;
        point.update(kakaoPlaceId, name, address, mapX, mapY, assignmentOrder);
        return point;
    }

    public void update(String kakaoPlaceId, String name, String address,
            BigDecimal mapX, BigDecimal mapY, int assignmentOrder) {
        this.kakaoPlaceId = kakaoPlaceId.trim();
        this.name = name.trim();
        this.address = address.trim();
        this.mapX = mapX;
        this.mapY = mapY;
        this.assignmentOrder = assignmentOrder;
    }

    public void changeStatus(FestivalMeetingPointStatus status) {
        this.status = status;
    }

    @PrePersist void prePersist() {
        OffsetDateTime now = SeoulDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = SeoulDateTime.now(); }

    public Long getId() { return id; }
    public Long getFestivalId() { return festivalId; }
    public String getKakaoPlaceId() { return kakaoPlaceId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public BigDecimal getMapX() { return mapX; }
    public BigDecimal getMapY() { return mapY; }
    public FestivalMeetingPointStatus getStatus() { return status; }
    public Integer getAssignmentOrder() { return assignmentOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
