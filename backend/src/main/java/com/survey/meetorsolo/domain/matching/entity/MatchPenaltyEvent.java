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
@Table(name = "match_penalty_events")
public class MatchPenaltyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "score_delta", nullable = false)
    private Integer scoreDelta;

    @Column(length = 255)
    private String reason;

    @Column(name = "related_attempt_id")
    private Long relatedAttemptId;
    @Column(name = "related_group_id")
    private Long relatedGroupId;

    @Column(name = "related_proposal_id")
    private Long relatedProposalId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MatchPenaltyEvent() {
    }

    public static MatchPenaltyEvent of(
            long memberId,
            String eventType,
            int scoreDelta,
            String reason,
            long relatedAttemptId,
            long relatedProposalId,
            OffsetDateTime now
    ) {
        MatchPenaltyEvent event = new MatchPenaltyEvent();
        event.memberId = memberId;
        event.eventType = eventType;
        event.scoreDelta = scoreDelta;
        event.reason = reason;
        event.relatedAttemptId = relatedAttemptId;
        event.relatedProposalId = relatedProposalId;
        event.createdAt = now;
        return event;
    }

    @Column(name = "related_pool_id")
    private Long relatedPoolId;

    @Column(name = "related_report_id")
    private Long relatedReportId;

    @Column(name = "manner_temperature_delta", precision = 5, scale = 2)
    private BigDecimal mannerTemperatureDelta;

    /**
     * 관리자가 유효하다고 판정한 신고의 penalty event.
     * {@code relatedReportId} 부분 unique index가 유효 판정 1건당 1건을 보장한다.
     * {@code mannerTemperatureDelta}에는 하한 clamp 이후 실제로 적용된 차감량을 음수로 저장한다.
     */
    public static MatchPenaltyEvent forReportConfirmed(
            long memberId,
            int scoreDelta,
            BigDecimal mannerTemperatureDelta,
            String reason,
            long reportId,
            OffsetDateTime now
    ) {
        MatchPenaltyEvent event = new MatchPenaltyEvent();
        event.memberId = memberId;
        event.eventType = "REPORT_CONFIRMED";
        event.scoreDelta = scoreDelta;
        event.mannerTemperatureDelta = mannerTemperatureDelta;
        event.reason = reason;
        event.relatedReportId = reportId;
        event.createdAt = now;
        return event;
    }

    public static MatchPenaltyEvent forPoolCancel(long memberId, int scoreDelta,
            String reason, long poolId, OffsetDateTime now) {
        MatchPenaltyEvent event = new MatchPenaltyEvent();
        event.memberId = memberId;
        event.eventType = "POOL_CANCEL";
        event.scoreDelta = scoreDelta;
        event.reason = reason;
        event.relatedPoolId = poolId;
        event.createdAt = now;
        return event;
    }

    public static MatchPenaltyEvent forGroup(long memberId, String eventType, int scoreDelta,
            String reason, long groupId, long attemptId, OffsetDateTime now) {
        MatchPenaltyEvent event = new MatchPenaltyEvent();
        event.memberId = memberId;
        event.eventType = eventType;
        event.scoreDelta = scoreDelta;
        event.reason = reason;
        event.relatedGroupId = groupId;
        event.relatedAttemptId = attemptId;
        event.createdAt = now;
        return event;
    }
}
