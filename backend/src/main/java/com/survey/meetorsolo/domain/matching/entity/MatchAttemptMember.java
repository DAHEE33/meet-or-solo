package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "match_attempt_members")
public class MatchAttemptMember {
    public static final String STATUS_PROPOSED = "PROPOSED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_EXCLUDED = "EXCLUDED";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "attempt_id", nullable = false) private Long attemptId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "pool_id", nullable = false) private Long poolId;
    @Column(name = "member_score", nullable = false, precision = 10, scale = 2) private BigDecimal memberScore;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    protected MatchAttemptMember() { }
    public static MatchAttemptMember proposed(long attemptId, long memberId, long poolId,
                                               BigDecimal memberScore, OffsetDateTime now) {
        MatchAttemptMember member = new MatchAttemptMember();
        member.attemptId = attemptId; member.memberId = memberId; member.poolId = poolId;
        member.memberScore = memberScore; member.status = STATUS_PROPOSED;
        member.createdAt = now; member.updatedAt = now;
        return member;
    }
    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public Long getMemberId() { return memberId; }
    public Long getPoolId() { return poolId; }
    public String getStatus() { return status; }
    public void respond(String response, OffsetDateTime now) {
        if (!STATUS_PROPOSED.equals(status)) throw new IllegalStateException("PROPOSED member만 응답할 수 있습니다.");
        status = response; updatedAt = now;
    }
    public void exclude(OffsetDateTime now) {
        if (STATUS_PROPOSED.equals(status)) { status = STATUS_EXCLUDED; updatedAt = now; }
    }
}
