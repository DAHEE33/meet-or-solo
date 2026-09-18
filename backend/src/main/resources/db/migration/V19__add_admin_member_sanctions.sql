ALTER TABLE members
    DROP CONSTRAINT chk_members_status;

ALTER TABLE members
    ADD COLUMN suspended_at TIMESTAMPTZ,
    ADD COLUMN suspended_until TIMESTAMPTZ,
    ADD COLUMN status_before_sanction VARCHAR(30),
    ADD CONSTRAINT chk_members_status CHECK (
        status IN ('ACTIVE', 'PROFILE_REQUIRED', 'SUSPENDED', 'BANNED', 'WITHDRAWN', 'DELETED')
    ),
    ADD CONSTRAINT chk_members_status_before_sanction CHECK (
        status_before_sanction IS NULL OR status_before_sanction IN ('ACTIVE', 'PROFILE_REQUIRED')
    ),
    ADD CONSTRAINT chk_members_suspension_period CHECK (
        (status = 'SUSPENDED'
            AND suspended_at IS NOT NULL
            AND suspended_until IS NOT NULL
            AND suspended_until > suspended_at
            AND status_before_sanction IS NOT NULL)
        OR
        (status <> 'SUSPENDED' AND suspended_at IS NULL AND suspended_until IS NULL)
    ),
    ADD CONSTRAINT chk_members_banned_previous_status CHECK (
        status <> 'BANNED' OR status_before_sanction IS NOT NULL
    );

CREATE INDEX idx_members_suspended_until
    ON members (suspended_until, id)
    WHERE status = 'SUSPENDED';

ALTER TABLE admin_actions
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN reason_code VARCHAR(40),
    ADD CONSTRAINT chk_admin_actions_reason_code CHECK (
        reason_code IS NULL OR reason_code IN (
            'COMMUNITY_GUIDELINE',
            'HARASSMENT',
            'NO_SHOW_ABUSE',
            'FRAUD_OR_SCAM',
            'SAFETY_RISK',
            'ADMIN_CORRECTION',
            'OTHER'
        )
    );

CREATE UNIQUE INDEX uq_admin_actions_idempotency_key
    ON admin_actions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

