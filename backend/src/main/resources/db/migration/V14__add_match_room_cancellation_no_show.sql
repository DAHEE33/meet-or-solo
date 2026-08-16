ALTER TABLE match_group_members
    ADD COLUMN allow_minimum_two BOOLEAN,
    ADD COLUMN no_show_at TIMESTAMPTZ;

UPDATE match_group_members group_member
SET allow_minimum_two = pool.allow_minimum_two
FROM match_groups matching_group
JOIN match_attempt_members attempt_member
  ON attempt_member.attempt_id = matching_group.attempt_id
JOIN match_pools pool
  ON pool.id = attempt_member.pool_id
WHERE matching_group.id = group_member.group_id
  AND attempt_member.member_id = group_member.member_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM match_group_members
        WHERE allow_minimum_two IS NULL
    ) THEN
        RAISE EXCEPTION 'match_group_members.allow_minimum_two backfill failed';
    END IF;
END
$$;

ALTER TABLE match_group_members
    ALTER COLUMN allow_minimum_two SET NOT NULL,
    ADD CONSTRAINT chk_match_group_members_cancel_reason
        CHECK (
            cancel_reason IS NULL
            OR cancel_reason IN (
                'SCHEDULE_CHANGED',
                'TRANSPORTATION_ISSUE',
                'OTHER'
            )
        );

ALTER TABLE match_groups
    ADD COLUMN cancel_reason VARCHAR(60),
    ADD CONSTRAINT chk_match_groups_cancel_reason
        CHECK (
            cancel_reason IS NULL
            OR cancel_reason IN (
                'INSUFFICIENT_ACTIVE_MEMBERS',
                'MINIMUM_TWO_NOT_ALLOWED'
            )
        );

ALTER TABLE match_events
    DROP CONSTRAINT chk_match_events_type;

ALTER TABLE match_events
    ADD CONSTRAINT chk_match_events_type CHECK (event_type IN (
        'MATCH_PROPOSED',
        'MATCH_ACCEPTED',
        'MATCH_REJECTED',
        'MATCH_TIMEOUT',
        'MATCH_INSUFFICIENT_MEMBERS',
        'MATCH_CONFIRMED',
        'ARRIVAL_TIME_SELECTED',
        'MEMBER_ARRIVED',
        'MEMBER_CANCELLED',
        'MEMBER_NO_SHOW',
        'MATCH_CANCELLED',
        'SAFETY_REMINDER'
    ));

ALTER TABLE match_cooldowns
    ADD COLUMN related_group_id BIGINT,
    ADD CONSTRAINT fk_match_cooldowns_related_group
        FOREIGN KEY (related_group_id) REFERENCES match_groups (id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_match_cooldowns_group_member_cause
    ON match_cooldowns (related_group_id, member_id, reason)
    WHERE related_group_id IS NOT NULL
      AND reason IN ('CANCEL', 'NO_SHOW');

CREATE UNIQUE INDEX uq_match_penalty_events_group_member_cause
    ON match_penalty_events (related_group_id, member_id, event_type)
    WHERE related_group_id IS NOT NULL
      AND event_type IN ('CANCEL', 'NO_SHOW');

CREATE INDEX idx_match_groups_no_show_deadline
    ON match_groups (confirmed_at, id)
    WHERE status IN ('CONFIRMED', 'IN_PROGRESS');

CREATE INDEX idx_match_penalty_events_member_type_created_at
    ON match_penalty_events (member_id, event_type, created_at);
