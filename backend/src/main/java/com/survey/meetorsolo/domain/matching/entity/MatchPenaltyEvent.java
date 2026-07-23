package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
