-- 매칭 탐색 자발적 취소 cooldown/penalty를 pool 기반으로 추적하기 위한 컬럼 추가
ALTER TABLE match_cooldowns ADD COLUMN related_pool_id BIGINT;
ALTER TABLE match_penalty_events ADD COLUMN related_pool_id BIGINT;

-- pool 기반 멱등성 제약: 같은 pool에 대해 중복 cooldown 방지
CREATE UNIQUE INDEX uq_match_cooldowns_pool
    ON match_cooldowns (related_pool_id)
    WHERE related_pool_id IS NOT NULL;

CREATE UNIQUE INDEX uq_match_penalty_events_pool
    ON match_penalty_events (related_pool_id)
    WHERE related_pool_id IS NOT NULL;

-- cooldown reason에 POOL_CANCEL 허용
ALTER TABLE match_cooldowns DROP CONSTRAINT chk_match_cooldowns_reason;
ALTER TABLE match_cooldowns ADD CONSTRAINT chk_match_cooldowns_reason
    CHECK (reason IN ('REJECT', 'TIMEOUT', 'CANCEL', 'NO_SHOW', 'REPORT', 'POOL_CANCEL'));

-- penalty event_type에 POOL_CANCEL 허용
ALTER TABLE match_penalty_events DROP CONSTRAINT chk_match_penalty_events_type;
ALTER TABLE match_penalty_events ADD CONSTRAINT chk_match_penalty_events_type
    CHECK (event_type IN ('TIMEOUT', 'CANCEL', 'NO_SHOW', 'REPORT_CONFIRMED', 'ADMIN_ADJUST', 'DECAY', 'POOL_CANCEL'));
