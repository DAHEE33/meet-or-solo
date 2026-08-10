ALTER TABLE match_group_members
    DROP CONSTRAINT chk_match_group_members_status;

ALTER TABLE match_group_members
    ADD CONSTRAINT chk_match_group_members_status CHECK (status IN (
        'JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED', 'COMPLETED',
        'CANCELLED', 'NO_SHOW', 'LEFT'
    ));

ALTER TABLE match_events
    DROP CONSTRAINT chk_match_events_type;

ALTER TABLE match_events
    ADD CONSTRAINT chk_match_events_type CHECK (event_type IN (
        'MATCH_PROPOSED', 'MATCH_ACCEPTED', 'MATCH_REJECTED', 'MATCH_TIMEOUT',
        'MATCH_INSUFFICIENT_MEMBERS', 'MATCH_CONFIRMED', 'ARRIVAL_TIME_SELECTED',
        'MEMBER_ARRIVED', 'MEMBER_CANCELLED', 'MEMBER_NO_SHOW', 'MATCH_CANCELLED',
        'MATCH_COMPLETED', 'SAFETY_REMINDER'
    ));

-- 과거 COMPLETED group이 active member 상태를 남겼다면 완료 시각과 점유를 정리한다.
UPDATE match_groups
SET completed_at = COALESCE(completed_at, updated_at, started_at, confirmed_at)
WHERE status = 'COMPLETED'
  AND completed_at IS NULL;

UPDATE match_group_members group_member
SET status = 'COMPLETED',
    updated_at = matching_group.completed_at
FROM match_groups matching_group
WHERE matching_group.id = group_member.group_id
  AND matching_group.status = 'COMPLETED'
  AND group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED');

INSERT INTO match_events (group_id, attempt_id, event_type, payload, created_at)
SELECT matching_group.id,
       matching_group.attempt_id,
       'MATCH_COMPLETED',
       '{}'::jsonb,
       matching_group.completed_at
FROM match_groups matching_group
WHERE matching_group.status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1
      FROM match_events event
      WHERE event.group_id = matching_group.id
        AND event.event_type = 'MATCH_COMPLETED'
  );

CREATE UNIQUE INDEX uq_match_events_group_completed
    ON match_events (group_id)
    WHERE event_type = 'MATCH_COMPLETED';

DROP INDEX uq_match_group_members_member_active;

CREATE UNIQUE INDEX uq_match_group_members_member_active
    ON match_group_members (member_id)
    WHERE status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED');
