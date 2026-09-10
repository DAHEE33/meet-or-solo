package com.survey.meetorsolo.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 매너온도 상승 이력({@code docs/19} 4.9).
 *
 * <p><b>상승만 담는다.</b> 하강(신고 확정)은 기존 {@code match_penalty_events}의
 * {@code manner_temperature_delta}가, 관리자 수동 조정은 {@code admin_actions}의
 * {@code MANNER_TEMPERATURE_ADJUST}가 이미 기록한다. 기존 두 경로를 이쪽으로 옮기지 않는다.
 */
@Entity
@Table(name = "manner_temperature_events")
public class MannerTemperatureEvent {

    /** 만남을 끝까지 마쳐서 받은 보상. */
    public static final String TYPE_MATCH_COMPLETED = "MATCH_COMPLETED";

    /** 시간 경과 회복. */
    public static final String TYPE_TIME_RECOVERY = "TIME_RECOVERY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "delta", nullable = false, precision = 5, scale = 2)
    private BigDecimal delta;

    @Column(name = "before_temperature", nullable = false, precision = 5, scale = 2)
    private BigDecimal beforeTemperature;

    @Column(name = "after_temperature", nullable = false, precision = 5, scale = 2)
    private BigDecimal afterTemperature;

    @Column(name = "related_group_id")
    private Long relatedGroupId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MannerTemperatureEvent() {
    }

    public static MannerTemperatureEvent matchCompleted(
            long memberId, BigDecimal delta, BigDecimal before, BigDecimal after,
            long groupId, OffsetDateTime now) {
        return of(memberId, TYPE_MATCH_COMPLETED, delta, before, after, groupId, now);
    }

    public static MannerTemperatureEvent timeRecovery(
            long memberId, BigDecimal delta, BigDecimal before, BigDecimal after,
            OffsetDateTime now) {
        return of(memberId, TYPE_TIME_RECOVERY, delta, before, after, null, now);
    }

    private static MannerTemperatureEvent of(
            long memberId, String eventType, BigDecimal delta, BigDecimal before,
            BigDecimal after, Long groupId, OffsetDateTime now) {
        if (delta == null || delta.signum() <= 0) {
            throw new IllegalArgumentException("상승량이 0이면 이력을 남기지 않는다.");
        }
        MannerTemperatureEvent event = new MannerTemperatureEvent();
        event.memberId = memberId;
        event.eventType = eventType;
        event.delta = delta;
        event.beforeTemperature = before;
        event.afterTemperature = after;
        event.relatedGroupId = groupId;
        event.createdAt = now;
        return event;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getEventType() {
        return eventType;
    }

    public BigDecimal getDelta() {
        return delta;
    }

    public BigDecimal getBeforeTemperature() {
        return beforeTemperature;
    }

    public BigDecimal getAfterTemperature() {
        return afterTemperature;
    }

    public Long getRelatedGroupId() {
        return relatedGroupId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
