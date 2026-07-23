ALTER TABLE match_cooldowns
    ADD COLUMN related_proposal_id BIGINT;

ALTER TABLE match_cooldowns
    ADD CONSTRAINT fk_match_cooldowns_related_proposal
        FOREIGN KEY (related_proposal_id) REFERENCES match_proposals (id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_match_cooldowns_related_proposal
    ON match_cooldowns (related_proposal_id)
    WHERE related_proposal_id IS NOT NULL;

ALTER TABLE match_penalty_events
    ADD COLUMN related_proposal_id BIGINT;

ALTER TABLE match_penalty_events
    ADD CONSTRAINT fk_match_penalty_events_related_proposal
        FOREIGN KEY (related_proposal_id) REFERENCES match_proposals (id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_match_penalty_events_related_proposal
    ON match_penalty_events (related_proposal_id)
    WHERE related_proposal_id IS NOT NULL;
