UPDATE festival_checkins
SET expires_at = checked_in_at + INTERVAL '1 hour',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE'
  AND expires_at > checked_in_at + INTERVAL '1 hour';

UPDATE festival_checkins
SET status = 'EXPIRED',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE'
  AND expires_at <= CURRENT_TIMESTAMP;
