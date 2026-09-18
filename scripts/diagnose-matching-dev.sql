-- dev에서 "여러 명이 같은 희망 인원으로 신청했는데 매칭이 안 된다"를 판별하는 읽기 전용 진단입니다.
--
-- 조합 로직 자체는 통합 테스트로 검증돼 있습니다
-- (MatchingThreeMemberScenarioIntegrationTest: scheduler tick 경로와 pool entry 경로 모두
-- 희망 인원 3명 3인이 모이면 proposal을 만듭니다). 따라서 dev에서 안 되면 원인은 아래 중
-- 하나입니다. 위에서부터 순서대로 보면 됩니다.
--
-- 실행:
--   1. scripts/start-dev-db-tunnel.ps1 로 터널을 연다 (localhost:15432)
--   2. psql -h localhost -p 15432 -U <USER> -d meet_or_solo_dev -f scripts/diagnose-matching-dev.sql
--
-- 아무 행도 쓰지 않습니다.

\echo '=== [1] scheduler가 살아 있는가 =========================================='
-- search_expires_at이 지났는데 아직 WAITING인 pool은 cleanup이 안 돌았다는 뜻입니다.
-- MatchPoolCleanupService는 MatchingOrchestrationService.runTick()에서만 호출되고,
-- runTick은 MATCHING_SCHEDULER_ENABLED=true일 때만 5초마다 돕니다.
-- stuck_waiting이 0보다 크면 scheduler가 멈춰 있습니다. 이것부터 고쳐야 합니다.
SELECT
    count(*) FILTER (WHERE status = 'WAITING' AND search_expires_at <= now()) AS stuck_waiting,
    count(*) FILTER (WHERE status = 'LOCKED')                                 AS still_locked,
    max(updated_at)                                                           AS last_pool_update
FROM match_pools;

\echo '=== [2] 최근 매칭 시도가 만들어지고 있는가 ================================'
-- 행이 있으면 그룹 구성까지는 성공한 것이고, 문제는 제안 응답 단계입니다.
-- 비어 있으면 그룹 자체가 안 만들어졌다는 뜻이니 [3]~[6]을 봅니다.
SELECT id, festival_id, target_group_size, status, created_by, started_at, expires_at
FROM match_attempts
WHERE started_at > now() - INTERVAL '2 hours'
ORDER BY id DESC
LIMIT 20;

\echo '=== [3] 최근 pool 신청 내역 =============================================='
-- 같은 festival_id + 같은 preferred_group_size 조합이 목표 인원만큼 "동시에" WAITING이어야
-- 그룹이 만들어집니다. entered_at ~ search_expires_at 구간이 서로 겹치는지 눈으로 봅니다.
-- 희망 인원이 하나라도 다르면 절대 섞이지 않습니다.
SELECT
    id, member_id, festival_id, checkin_id, preferred_group_size AS size,
    allow_minimum_two AS min2, status, entered_at, search_expires_at,
    search_expires_at - entered_at AS search_window
FROM match_pools
WHERE entered_at > now() - INTERVAL '2 hours'
ORDER BY entered_at DESC
LIMIT 30;

\echo '=== [4] 축제/희망인원별로 동시 대기 인원이 목표에 닿았는가 ================'
-- 같은 축제 + 같은 희망 인원으로 탐색 구간이 겹친 최대 인원 수입니다.
-- max_concurrent < size 이면 애초에 그룹이 성립할 수 없습니다. 가장 흔한 원인입니다.
WITH recent AS (
    SELECT festival_id, preferred_group_size, entered_at, search_expires_at
    FROM match_pools
    WHERE entered_at > now() - INTERVAL '2 hours'
)
SELECT
    a.festival_id,
    a.preferred_group_size AS size,
    max(concurrent.count) AS max_concurrent
FROM recent a
CROSS JOIN LATERAL (
    SELECT count(*) AS count
    FROM recent b
    WHERE b.festival_id = a.festival_id
      AND b.preferred_group_size = a.preferred_group_size
      AND b.entered_at <= a.search_expires_at
      AND b.search_expires_at >= a.entered_at
) AS concurrent
GROUP BY a.festival_id, a.preferred_group_size
ORDER BY a.festival_id, a.preferred_group_size;

\echo '=== [5] 체크인이 유효한가 ================================================'
-- 체크인은 status=ACTIVE이고 LEAST(expires_at, checked_in_at + 1시간) > now() 여야 합니다.
-- valid=false인 pool은 claim 쿼리에서 통째로 빠집니다.
SELECT
    p.id AS pool_id, p.member_id, c.status AS checkin_status,
    c.checked_in_at, c.expires_at,
    LEAST(c.expires_at, c.checked_in_at + INTERVAL '1 hour') AS effective_expiry,
    (c.status = 'ACTIVE'
     AND LEAST(c.expires_at, c.checked_in_at + INTERVAL '1 hour') > now()) AS valid
FROM match_pools p
JOIN festival_checkins c ON c.id = p.checkin_id
WHERE p.entered_at > now() - INTERVAL '2 hours'
ORDER BY p.entered_at DESC
LIMIT 30;

\echo '=== [6] 쿨타임/차단/거절 이력이 후보를 잘라내는가 ========================='
-- 쿨타임이 걸린 회원은 claim에서 제외되고, 신규 신청 자체가 409로 막힙니다.
SELECT member_id, reason, status, starts_at, expires_at
FROM match_cooldowns
WHERE status = 'ACTIVE' AND expires_at > now()
ORDER BY expires_at DESC;

-- 거절 이력은 "그 체크인 쌍"에 대해 남습니다. 체크인을 새로 하면 checkin_id가 바뀌어 풀립니다.
-- 여기 남아 있는 쌍이 지금 대기 중인 회원들의 현재 checkin_id와 일치하면 그 조합은 제외됩니다.
SELECT lower_member_id, higher_member_id, lower_checkin_id, higher_checkin_id,
       rejected_by_member_id, created_at
FROM match_opponent_exclusions
WHERE created_at > now() - INTERVAL '1 day'
ORDER BY created_at DESC
LIMIT 30;

SELECT blocker_member_id, blocked_member_id, created_at FROM user_blocks ORDER BY created_at DESC LIMIT 20;

\echo '=== [7] 축제에 ACTIVE 만남 장소가 있는가 =================================='
-- 없으면 pool 진입 자체가 MATCHING_MEETING_POINT_NOT_READY로 막힙니다.
SELECT f.id AS festival_id, f.title,
       count(*) FILTER (WHERE mp.status = 'ACTIVE') AS active_meeting_points
FROM festivals f
LEFT JOIN festival_meeting_points mp ON mp.festival_id = f.id
WHERE f.id IN (SELECT DISTINCT festival_id FROM match_pools WHERE entered_at > now() - INTERVAL '2 hours')
GROUP BY f.id, f.title;
