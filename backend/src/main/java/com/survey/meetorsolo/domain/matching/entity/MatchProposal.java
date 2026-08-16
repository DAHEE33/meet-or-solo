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
    public static final String TYPE_INSUFFICIENT_MEMBERS_CONFIRMATION = "INSUFFICIENT_MEMBERS_CONFIRMATION";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_EXPIRED = "EXPIRED";
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
        return create(attemptId, memberId, TYPE_INITIAL_MATCH, 1, now, expiresAt);
    }
    public static MatchProposal insufficientMembers(long attemptId, long memberId,
            OffsetDateTime now, OffsetDateTime expiresAt) {
        return create(attemptId, memberId, TYPE_INSUFFICIENT_MEMBERS_CONFIRMATION, 2, now, expiresAt);
    }
    private static MatchProposal create(long attemptId, long memberId, String type, int round,
            OffsetDateTime now, OffsetDateTime expiresAt) {
        MatchProposal proposal = new MatchProposal();
        proposal.attemptId = attemptId; proposal.memberId = memberId;
        proposal.proposalType = type; proposal.proposalRound = round; proposal.status = STATUS_SENT;
        proposal.sentAt = now; proposal.expiresAt = expiresAt; proposal.createdAt = now; proposal.updatedAt = now;
        return proposal;
    }
    public Long getId() { return id; }
    public Long getAttemptId() { return attemptId; }
    public Long getMemberId() { return memberId; }
    public String getProposalType() { return proposalType; }
    public Integer getProposalRound() { return proposalRound; }
    public String getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void respond(String response, OffsetDateTime now) {
        if (!STATUS_SENT.equals(status)) throw new IllegalStateException("SENT proposal만 응답할 수 있습니다.");
        status = response; respondedAt = now; updatedAt = now;
    }
    public void expire(OffsetDateTime now) {
        if (STATUS_SENT.equals(status)) { status = STATUS_EXPIRED; updatedAt = now; }
    }
}
