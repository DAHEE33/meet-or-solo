package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "match_proposals")
public class MatchProposal {
    public static final String TYPE_INITIAL_MATCH = "INITIAL_MATCH";
    public static final String STATUS_SENT = "SENT";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "attempt_id", nullable = false) private Long attemptId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "proposal_type", nullable = false, length = 40) private String proposalType;
    @Column(name = "proposal_round", nullable = false) private Integer proposalRound;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "sent_at", nullable = false) private OffsetDateTime sentAt;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "responded_at") private OffsetDateTime respondedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    protected MatchProposal() { }
    public static MatchProposal initial(long attemptId, long memberId, OffsetDateTime now, OffsetDateTime expiresAt) {
        MatchProposal proposal = new MatchProposal();
        proposal.attemptId = attemptId; proposal.memberId = memberId;
        proposal.proposalType = TYPE_INITIAL_MATCH; proposal.proposalRound = 1; proposal.status = STATUS_SENT;
        proposal.sentAt = now; proposal.expiresAt = expiresAt; proposal.createdAt = now; proposal.updatedAt = now;
        return proposal;
    }
}
