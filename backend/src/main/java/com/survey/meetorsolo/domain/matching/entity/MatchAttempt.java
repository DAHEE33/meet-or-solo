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
    public static final String STATUS_INSUFFICIENT_MEMBERS = "INSUFFICIENT_MEMBERS";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_FAILED = "FAILED";
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
    public Long getFestivalId() { return festivalId; }
    public Integer getTargetGroupSize() { return targetGroupSize; }
    public String getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void enterInsufficientMembers(OffsetDateTime now, OffsetDateTime nextExpiresAt) {
        requireStatus(STATUS_WAITING_RESPONSES);
        status = STATUS_INSUFFICIENT_MEMBERS;
        expiresAt = nextExpiresAt;
        updatedAt = now;
    }
    public void confirm(OffsetDateTime now) {
        requireActive(); status = STATUS_CONFIRMED; confirmedAt = now; updatedAt = now;
    }
    public void fail(String reason, OffsetDateTime now) {
        requireActive(); status = STATUS_FAILED; failedReason = reason; updatedAt = now;
    }
    private void requireActive() {
        if (!STATUS_WAITING_RESPONSES.equals(status) && !STATUS_INSUFFICIENT_MEMBERS.equals(status)) {
            throw new IllegalStateException("응답 대기 중인 attempt만 변경할 수 있습니다.");
        }
    }
    private void requireStatus(String expected) {
        if (!expected.equals(status)) throw new IllegalStateException("attempt 상태 전이가 올바르지 않습니다.");
    }
}
