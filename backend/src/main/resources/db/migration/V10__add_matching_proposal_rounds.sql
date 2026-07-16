ALTER TABLE match_proposals
    ADD COLUMN proposal_type VARCHAR(40) NOT NULL DEFAULT 'INITIAL_MATCH',
    ADD COLUMN proposal_round INTEGER NOT NULL DEFAULT 1;

ALTER TABLE match_proposals
    DROP CONSTRAINT uq_match_proposals_attempt_member;

ALTER TABLE match_proposals
    ADD CONSTRAINT uq_match_proposals_attempt_member_round
        UNIQUE (attempt_id, member_id, proposal_round),
    ADD CONSTRAINT chk_match_proposals_type
        CHECK (proposal_type IN (
            'INITIAL_MATCH',
            'INSUFFICIENT_MEMBERS_CONFIRMATION'
        )),
    ADD CONSTRAINT chk_match_proposals_round
        CHECK (proposal_round > 0);

CREATE INDEX idx_match_proposals_attempt_type_round
    ON match_proposals (attempt_id, proposal_type, proposal_round);
