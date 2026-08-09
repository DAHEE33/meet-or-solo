package com.survey.meetorsolo.domain.matching.entity;

import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPoint;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity @Table(name = "match_groups")
public class MatchGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_id", nullable=false) private Long attemptId;
    @Column(name="festival_id", nullable=false) private Long festivalId;
    @Column(nullable=false, length=30) private String status;
    @Column(name="confirmed_member_count", nullable=false) private Integer confirmedMemberCount;
    @Column(name="meeting_place_name") private String meetingPlaceName;
    @Column(name="meeting_place_address", length=500) private String meetingPlaceAddress;
    @Column(name="meeting_place_content_id", length=50) private String meetingPlaceContentId;
    @Column(name="meeting_map_x", precision=13, scale=10) private BigDecimal meetingMapX;
    @Column(name="meeting_map_y", precision=13, scale=10) private BigDecimal meetingMapY;
    @Column(name="confirmed_at", nullable=false) private OffsetDateTime confirmedAt;
    @Column(name="started_at") private OffsetDateTime startedAt;
    @Column(name="cancelled_at") private OffsetDateTime cancelledAt;
    @Column(name="cancel_reason", length=60) private String cancelReason;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
    protected MatchGroup() { }
    public static MatchGroup confirmed(long attemptId, long festivalId, int count,
            FestivalMeetingPoint meetingPoint, OffsetDateTime now) {
        if (meetingPoint == null) throw new IllegalArgumentException("meetingPoint는 필수입니다.");
        MatchGroup group = new MatchGroup(); group.attemptId=attemptId; group.festivalId=festivalId;
        group.status="CONFIRMED"; group.confirmedMemberCount=count; group.confirmedAt=now;
        group.meetingPlaceName=meetingPoint.getName(); group.meetingPlaceAddress=meetingPoint.getAddress();
        group.meetingPlaceContentId=meetingPoint.getKakaoPlaceId();
        group.meetingMapX=meetingPoint.getMapX(); group.meetingMapY=meetingPoint.getMapY();
        group.createdAt=now; group.updatedAt=now; return group;
    }
    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public Long getFestivalId() { return festivalId; }
    public String getStatus() { return status; }
    public Integer getConfirmedMemberCount() { return confirmedMemberCount; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public String getMeetingPlaceName() { return meetingPlaceName; }
    public String getMeetingPlaceAddress() { return meetingPlaceAddress; }
    public String getMeetingPlaceContentId() { return meetingPlaceContentId; }
    public BigDecimal getMeetingMapX() { return meetingMapX; }
    public BigDecimal getMeetingMapY() { return meetingMapY; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void start(OffsetDateTime now) {
        if ("CONFIRMED".equals(status)) {
            status = "IN_PROGRESS";
            startedAt = now;
            updatedAt = now;
        }
    }
    public void cancel(String reason, OffsetDateTime now) {
        if (!"CANCELLED".equals(status)) {
            status = "CANCELLED";
            cancelReason = reason;
            cancelledAt = now;
            updatedAt = now;
        }
    }
}
