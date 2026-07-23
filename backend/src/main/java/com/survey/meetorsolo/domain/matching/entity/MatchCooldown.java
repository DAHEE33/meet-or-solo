package com.survey.meetorsolo.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "match_cooldowns")
public class MatchCooldown {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "related_proposal_id")
    private Long relatedProposalId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MatchCooldown() {
    }

    public static MatchCooldown active(
            long memberId,
            String reason,
            long relatedProposalId,
            OffsetDateTime startsAt,
            OffsetDateTime expiresAt
    ) {
        MatchCooldown cooldown = new MatchCooldown();
        cooldown.memberId = memberId;
        cooldown.reason = reason;
        cooldown.status = STATUS_ACTIVE;
        cooldown.startsAt = startsAt;
        cooldown.expiresAt = expiresAt;
        cooldown.relatedProposalId = relatedProposalId;
        cooldown.createdAt = startsAt;
        return cooldown;
    }
}
