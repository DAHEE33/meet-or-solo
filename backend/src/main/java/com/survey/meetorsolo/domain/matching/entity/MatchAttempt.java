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
@Table(name = "match_attempts")
public class MatchAttempt {

    public static final String STATUS_WAITING_RESPONSES = "WAITING_RESPONSES";
    public static final String CREATED_BY_SCHEDULER = "SCHEDULER";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "festival_id", nullable = false) private Long festivalId;
    @Column(name = "target_group_size", nullable = false) private Integer targetGroupSize;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal score;
    @Column(name = "created_by", nullable = false, length = 30) private String createdBy;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "confirmed_at") private OffsetDateTime confirmedAt;
    @Column(name = "failed_reason", length = 100) private String failedReason;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected MatchAttempt() { }

    public static MatchAttempt initial(long festivalId, int groupSize, BigDecimal score,
                                       OffsetDateTime now, OffsetDateTime expiresAt) {
        MatchAttempt attempt = new MatchAttempt();
        attempt.festivalId = festivalId;
        attempt.targetGroupSize = groupSize;
        attempt.status = STATUS_WAITING_RESPONSES;
        attempt.score = score;
        attempt.createdBy = CREATED_BY_SCHEDULER;
        attempt.startedAt = now;
        attempt.expiresAt = expiresAt;
        attempt.createdAt = now;
        attempt.updatedAt = now;
        return attempt;
    }

    public Long getId() { return id; }
}
