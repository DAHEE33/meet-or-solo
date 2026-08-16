package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name = "match_responses")
public class MatchResponse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "proposal_id", nullable = false) private Long proposalId;
    @Column(name = "attempt_id", nullable = false) private Long attemptId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false, length = 40) private String response;
    @Column(name = "responded_at", nullable = false) private OffsetDateTime respondedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    protected MatchResponse() { }
    public static MatchResponse of(long proposalId, long attemptId, long memberId, String response, OffsetDateTime now) {
        MatchResponse value = new MatchResponse(); value.proposalId = proposalId; value.attemptId = attemptId;
        value.memberId = memberId; value.response = response; value.respondedAt = now; value.createdAt = now; return value;
    }
    public String getResponse() { return response; }
    public Long getProposalId() { return proposalId; }
    public Long getAttemptId() { return attemptId; }
    public Long getMemberId() { return memberId; }
}
