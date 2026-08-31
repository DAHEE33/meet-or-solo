package com.survey.meetorsolo.domain.festival.entity;

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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * GPS 체크인 1건. 원본 위경도는 이 엔티티에도, DB에도 저장하지 않는다 — 서버가 축제 좌표와의
 * 거리를 계산한 결과({@code distanceMeters})만 남긴다.
 */
@Entity
@Table(name = "festival_checkins")
public class FestivalCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FestivalCheckinStatus status = FestivalCheckinStatus.ACTIVE;

    @Column(name = "checked_in_at", nullable = false)
    private OffsetDateTime checkedInAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FestivalCheckin() {
    }

    public static FestivalCheckin create(
            Long memberId,
            Long festivalId,
            int distanceMeters,
            Duration validDuration
    ) {
        if (distanceMeters < 0) {
            throw new IllegalArgumentException("distanceMeters는 0 이상이어야 합니다.");
        }
        FestivalCheckin checkin = new FestivalCheckin();
        checkin.memberId = Objects.requireNonNull(memberId, "memberId");
        checkin.festivalId = Objects.requireNonNull(festivalId, "festivalId");
        checkin.distanceMeters = distanceMeters;
        checkin.status = FestivalCheckinStatus.ACTIVE;
        OffsetDateTime now = SeoulDateTime.now();
        checkin.checkedInAt = now;
        checkin.expiresAt = now.plus(validDuration);
        return checkin;
    }

    /** 같은 회원이 다른 축제(또는 같은 축제)에 새로 체크인할 때 기존 ACTIVE 체크인을 정리한다. */
    public void cancel() {
        this.status = FestivalCheckinStatus.CANCELLED;
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

    public int getDistanceMeters() {
        return distanceMeters;
    }

    public FestivalCheckinStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
