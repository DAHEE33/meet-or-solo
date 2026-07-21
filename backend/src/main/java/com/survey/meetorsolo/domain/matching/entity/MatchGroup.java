package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "match_groups")
public class MatchGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_id", nullable=false) private Long attemptId;
    @Column(name="festival_id", nullable=false) private Long festivalId;
    @Column(nullable=false, length=30) private String status;
    @Column(name="confirmed_member_count", nullable=false) private Integer confirmedMemberCount;
    @Column(name="confirmed_at", nullable=false) private OffsetDateTime confirmedAt;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
    protected MatchGroup() { }
    public static MatchGroup confirmed(long attemptId, long festivalId, int count, OffsetDateTime now) {
        MatchGroup group = new MatchGroup(); group.attemptId=attemptId; group.festivalId=festivalId;
        group.status="CONFIRMED"; group.confirmedMemberCount=count; group.confirmedAt=now;
        group.createdAt=now; group.updatedAt=now; return group;
    }
    public Long getId() { return id; }
}
