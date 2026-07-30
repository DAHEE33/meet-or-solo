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
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
    protected MatchGroupMember() { }
    public static MatchGroupMember joined(long groupId, long memberId, OffsetDateTime now) {
        MatchGroupMember member = new MatchGroupMember(); member.groupId=groupId; member.memberId=memberId;
        member.status="JOINED"; member.createdAt=now; member.updatedAt=now; return member;
    }
    public void selectArrivalTime(int minutes, OffsetDateTime now) {
        status = "ARRIVAL_TIME_SELECTED";
        arrivalMinutes = minutes;
        arrivalTimeSelectedAt = now;
        updatedAt = now;
    }
    public void arrive(OffsetDateTime now) {
        status = "ARRIVED";
        arrivedAt = now;
        updatedAt = now;
    }
    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getMemberId() { return memberId; }
    public String getStatus() { return status; }
    public Integer getArrivalMinutes() { return arrivalMinutes; }
    public OffsetDateTime getArrivalTimeSelectedAt() { return arrivalTimeSelectedAt; }
    public OffsetDateTime getArrivedAt() { return arrivedAt; }
}
