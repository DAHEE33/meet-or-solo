package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "match_events")
public class MatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "group_id")
    private Long groupId;
    @Column(name = "attempt_id")
    private Long attemptId;
    @Column(name = "member_id")
    private Long memberId;
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MatchEvent() {
    }

    public static MatchEvent matchConfirmed(
            long groupId,
            long attemptId,
            OffsetDateTime now
    ) {
        MatchEvent event = new MatchEvent();
        event.groupId = groupId;
        event.attemptId = attemptId;
        event.eventType = "MATCH_CONFIRMED";
        event.payload = Map.of();
        event.createdAt = now;
        return event;
    }

    public static MatchEvent arrivalTimeSelected(
            long groupId,
            long attemptId,
            long memberId,
            int arrivalMinutes,
            OffsetDateTime now
    ) {
        MatchEvent event = new MatchEvent();
        event.groupId = groupId;
        event.attemptId = attemptId;
        event.memberId = memberId;
        event.eventType = "ARRIVAL_TIME_SELECTED";
        event.payload = Map.of("arrivalMinutes", arrivalMinutes);
        event.createdAt = now;
        return event;
    }

    public static MatchEvent memberArrived(
            long groupId,
            long attemptId,
            long memberId,
            OffsetDateTime now
    ) {
        MatchEvent event = new MatchEvent();
        event.groupId = groupId;
        event.attemptId = attemptId;
        event.memberId = memberId;
        event.eventType = "MEMBER_ARRIVED";
        event.payload = Map.of();
        event.createdAt = now;
        return event;
    }

    public static MatchEvent matchCompleted(long groupId, long attemptId, OffsetDateTime now) {
        return groupEvent(groupId, attemptId, null, "MATCH_COMPLETED", Map.of(), now);
    }

    public static MatchEvent memberCancelled(long groupId, long attemptId, long memberId,
            String reason, OffsetDateTime now) {
        return groupEvent(groupId, attemptId, memberId, "MEMBER_CANCELLED",
                Map.of("reason", reason), now);
    }

    public static MatchEvent memberNoShow(long groupId, long attemptId, long memberId,
            OffsetDateTime now) {
        return groupEvent(groupId, attemptId, memberId, "MEMBER_NO_SHOW", Map.of(), now);
    }

    public static MatchEvent matchCancelled(long groupId, long attemptId, String reason,
            OffsetDateTime now) {
        return groupEvent(groupId, attemptId, null, "MATCH_CANCELLED",
                Map.of("reason", reason), now);
    }

    private static MatchEvent groupEvent(long groupId, long attemptId, Long memberId,
            String eventType, Map<String, Object> payload, OffsetDateTime now) {
        MatchEvent event = new MatchEvent();
        event.groupId = groupId;
        event.attemptId = attemptId;
        event.memberId = memberId;
        event.eventType = eventType;
        event.payload = payload;
        event.createdAt = now;
        return event;
    }
}
