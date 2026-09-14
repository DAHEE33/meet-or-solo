package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "match_group_members")
public class MatchGroupMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="group_id", nullable=false) private Long groupId;
    @Column(name="member_id", nullable=false) private Long memberId;
    @Column(nullable=false, length=40) private String status;
    @Column(name="arrival_minutes") private Integer arrivalMinutes;
    @Column(name="arrival_time_selected_at") private OffsetDateTime arrivalTimeSelectedAt;
    @Column(name="arrived_at") private OffsetDateTime arrivedAt;
    @Column(name="arrival_distance_meters") private Integer arrivalDistanceMeters;
    @Column(name="left_at") private OffsetDateTime leftAt;
    @Column(name="cancelled_at") private OffsetDateTime cancelledAt;
    @Column(name="cancel_reason", length=100) private String cancelReason;
    @Column(name="no_show_at") private OffsetDateTime noShowAt;
    @Column(name="allow_minimum_two", nullable=false) private Boolean allowMinimumTwo;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
    protected MatchGroupMember() { }
    public static MatchGroupMember joined(long groupId, long memberId, boolean allowMinimumTwo, OffsetDateTime now) {
        MatchGroupMember member = new MatchGroupMember(); member.groupId=groupId; member.memberId=memberId;
        member.status="JOINED"; member.allowMinimumTwo=allowMinimumTwo;
        member.createdAt=now; member.updatedAt=now; return member;
    }
    public void selectArrivalTime(int minutes, OffsetDateTime now) {
        status = "ARRIVAL_TIME_SELECTED";
        arrivalMinutes = minutes;
        arrivalTimeSelectedAt = now;
        updatedAt = now;
    }
    public void arrive(OffsetDateTime now, Integer distanceMeters) {
        status = "ARRIVED";
        arrivedAt = now;
        arrivalDistanceMeters = distanceMeters;
        updatedAt = now;
    }
    public void complete(OffsetDateTime now) {
        status = "COMPLETED";
        updatedAt = now;
    }
    public void cancel(String reason, OffsetDateTime now) {
        status = "CANCELLED";
        cancelReason = reason;
        cancelledAt = now;
        updatedAt = now;
    }
    public void noShow(OffsetDateTime now) {
        status = "NO_SHOW";
        noShowAt = now;
        updatedAt = now;
    }
    /** 그룹이 종료되면서 정리되는 이탈이다. 본인 의사가 아니므로 {@code left_at}을 남기지 않는다. */
    public void leave(OffsetDateTime now) {
        status = "LEFT";
        updatedAt = now;
    }

    /**
     * 본인이 "먼저 갈게요"로 나간다({@code docs/19} 4.11.3).
     *
     * <p>{@link #leave}와 상태는 같고 {@code left_at}으로 갈린다. 자동 정리와 본인 의사를 구분해야
     * 타임라인에 "먼저 갔어요"를 남길 수 있다. <b>보상 판정에는 쓰지 않는다</b> — 먼저 갔는지
     * 끝까지 있었는지로 보상을 가르지 않기로 했다.
     */
    public void leaveEarly(OffsetDateTime now) {
        status = "LEFT";
        leftAt = now;
        updatedAt = now;
    }
    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getMemberId() { return memberId; }
    public String getStatus() { return status; }
    public Integer getArrivalMinutes() { return arrivalMinutes; }
    public OffsetDateTime getArrivalTimeSelectedAt() { return arrivalTimeSelectedAt; }
    public OffsetDateTime getArrivedAt() { return arrivedAt; }
    public Integer getArrivalDistanceMeters() { return arrivalDistanceMeters; }
    public OffsetDateTime getLeftAt() { return leftAt; }
    public Boolean getAllowMinimumTwo() { return allowMinimumTwo; }
}
