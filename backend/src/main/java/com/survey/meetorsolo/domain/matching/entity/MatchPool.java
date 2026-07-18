package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "match_pools")
public class MatchPool {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_LOCKED = "LOCKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    @Column(name = "checkin_id", nullable = false)
    private Long checkinId;

    @Column(name = "preferred_group_size", nullable = false)
    private Integer preferredGroupSize;

    @Column(name = "allow_minimum_two", nullable = false)
    private Boolean allowMinimumTwo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "entered_at", nullable = false)
    private OffsetDateTime enteredAt;

    @Column(name = "search_expires_at", nullable = false)
    private OffsetDateTime searchExpiresAt;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "lock_token", length = 100)
    private String lockToken;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MatchPool() {
    }

    private MatchPool(
            Long memberId,
            Long festivalId,
            Long checkinId,
            Integer preferredGroupSize,
            Boolean allowMinimumTwo,
            List<String> tags,
            String status,
            OffsetDateTime enteredAt,
            OffsetDateTime searchExpiresAt
    ) {
        this.memberId = memberId;
        this.festivalId = festivalId;
        this.checkinId = checkinId;
        this.preferredGroupSize = preferredGroupSize;
        this.allowMinimumTwo = allowMinimumTwo;
        this.tags = List.copyOf(tags);
        this.status = status;
        this.enteredAt = enteredAt;
        this.searchExpiresAt = searchExpiresAt;
        this.createdAt = enteredAt;
        this.updatedAt = enteredAt;
    }

    public static MatchPool waiting(
            Long memberId,
            Long festivalId,
            Long checkinId,
            Integer preferredGroupSize,
            Boolean allowMinimumTwo,
            List<String> tags,
            OffsetDateTime enteredAt,
            OffsetDateTime searchExpiresAt
    ) {
        return new MatchPool(
                memberId,
                festivalId,
                checkinId,
                preferredGroupSize,
                allowMinimumTwo,
                tags,
                STATUS_WAITING,
                enteredAt,
                searchExpiresAt
        );
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

    public Long getCheckinId() {
        return checkinId;
    }

    public Integer getPreferredGroupSize() {
        return preferredGroupSize;
    }

    public Boolean getAllowMinimumTwo() {
        return allowMinimumTwo;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getEnteredAt() {
        return enteredAt;
    }

    public OffsetDateTime getSearchExpiresAt() {
        return searchExpiresAt;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }

    public String getLockToken() {
        return lockToken;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void lock(OffsetDateTime lockedAt, String lockToken) {
        if (!STATUS_WAITING.equals(status)) {
            throw new IllegalStateException("WAITING 상태의 match pool만 선점할 수 있습니다.");
        }

        this.status = STATUS_LOCKED;
        this.lockedAt = lockedAt;
        this.lockToken = lockToken;
        this.updatedAt = lockedAt;
    }
}
