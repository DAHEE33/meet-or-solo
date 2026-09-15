-- "현장에 가지 않았는데 체크인·도착이 막힌다"를 판별하는 진단 + 테스트 계정 지정 스크립트입니다.
--
-- 반경 검증을 건너뛰는 경로는 두 가지이고 의미가 다릅니다(docs/19 4.12, docs/32 3.2).
--   - 설정 app.festival.checkin.bypass-radius-check / app.matching.arrival.bypass-radius-check
--     → 환경 전체를 끕니다. 어느 환경에서도 기본값은 false입니다.
--   - members.test_account
--     → 그 계정만 면제합니다. 같은 환경에서 일반 계정은 검증을 그대로 받습니다.
--
-- 정식 경로는 두 번째입니다. 원래는 /admin/members 화면에서 지정하지만,
-- 슈퍼관리자 계정(ADMIN_LOCAL_USERNAME / ADMIN_LOCAL_PASSWORD)이 아직 없으면
-- 화면에 들어갈 수 없으므로 아래 [5]로 직접 지정합니다.
--
-- 실행:
--   로컬: psql -h localhost -p 5432 -U <USER> -d meet_or_solo -f scripts/diagnose-gps-bypass.sql
--   dev : scripts/start-dev-db-tunnel.ps1 로 터널을 연 뒤 -p 15432
--
-- [1]~[4]는 조회만 합니다. [5]만 쓰기이고 기본적으로 주석 처리돼 있습니다.
-- 공유 dev DB에서는 [5]를 쓰지 않습니다(docs/30 4.0 — 공유 DB는 조회만).

\echo '=== [1] 내 계정이 면제 대상인가 =========================================='
-- test_account가 false면 반경·정확도 검증을 그대로 받습니다. 이것이 가장 흔한 원인입니다.
-- status가 ACTIVE·PROFILE_REQUIRED가 아니면 관리자 화면에서도 지정할 수 없습니다
-- (AdminMemberService.validateTestAccountStatus).
SELECT
    id,
    nickname,
    provider,
    role,
    status,
    test_account
FROM members
WHERE status NOT IN ('WITHDRAWN', 'DELETED')
ORDER BY test_account DESC, id
LIMIT 20;

\echo '=== [2] 슈퍼관리자 계정이 있는가 ========================================='
-- 0건이면 ADMIN_LOCAL_USERNAME / ADMIN_LOCAL_PASSWORD 가 비어 있다는 뜻입니다.
-- SuperAdminAccountBootstrap은 둘 다 있을 때만 계정을 만듭니다. 계정이 없으면
-- /admin/login에 들어갈 수 없고, 그러면 테스트 계정을 화면에서 지정할 수 없습니다.
SELECT
    c.id,
    c.username,
    c.member_id,
    m.role,
    m.status,
    c.failed_attempts,
    c.locked_until,
    c.last_login_at
FROM admin_credentials c
         JOIN members m ON m.id = c.member_id;

\echo '=== [3] 막힌 이유가 정말 GPS인가 ========================================='
-- 면제 대상은 "반경과 정확도"뿐입니다. 아래가 걸려 있으면 test_account를 켜도 막힙니다.
--   valid_checkin   : 체크인이 ACTIVE이고 아직 만료되지 않았는가 (유효기간 1시간)
--   active_group    : 이미 활성 매칭 그룹에 들어가 있는가
--   active_cooldown : 노쇼·응답 누락·거절로 쿨타임이 걸려 있는가
SELECT
    m.id                                                                  AS member_id,
    (SELECT count(*)
     FROM festival_checkins fc
     WHERE fc.member_id = m.id
       AND fc.status = 'ACTIVE'
       AND fc.expires_at > now())                                         AS valid_checkin,
    (SELECT count(*)
     FROM match_group_members gm
              JOIN match_groups g ON g.id = gm.group_id
     WHERE gm.member_id = m.id
       AND g.status IN ('CONFIRMED', 'IN_PROGRESS'))                      AS active_group,
    (SELECT count(*)
     FROM match_cooldowns mc
     WHERE mc.member_id = m.id
       AND mc.status = 'ACTIVE'
       AND mc.expires_at > now())                                         AS active_cooldown,
    m.penalty_score,
    m.manner_temperature
FROM members m
WHERE m.status NOT IN ('WITHDRAWN', 'DELETED')
ORDER BY m.id
LIMIT 20;

\echo '=== [4] 최근 체크인이 실제로 얼마나 떨어져서 찍혔는가 ====================='
-- distance_meters는 체크인 성공 시에만 남습니다. 거절된 시도는 여기 없습니다.
-- 거절 사유는 서버 로그에서 확인합니다.
--   "GPS 반경 검증을 건너뛰고 체크인을 허용했습니다(사유=TEST_ACCOUNT|CONFIG_BYPASS)"
--   "도착 반경 검증을 건너뛰고 도착을 인정했습니다(사유=TEST_ACCOUNT|CONFIG_BYPASS)"
-- 위 WARN이 보이면 면제가 걸린 것이고, 안 보이는데 거절되면 면제가 안 걸린 것입니다.
SELECT
    fc.id,
    fc.member_id,
    fc.festival_id,
    fc.distance_meters,
    f.checkin_radius_meters,
    fc.status,
    fc.checked_in_at,
    fc.expires_at
FROM festival_checkins fc
         JOIN festivals f ON f.id = fc.festival_id
ORDER BY fc.checked_in_at DESC
LIMIT 10;

\echo '=== [5] 테스트 계정 지정·해제 (쓰기 — 주석을 풀어서 사용) ================='
-- 관리자 화면(/admin/members)이 정식 경로입니다. 슈퍼관리자 계정이 아직 없을 때만
-- 아래를 씁니다. :a, :b 를 [1]에서 확인한 member id로 바꾸세요.
--
-- 지정
-- UPDATE members SET test_account = TRUE  WHERE id IN (:a, :b);
--
-- 해제 — 반경 검증이 실제로 동작하는지 확인할 때는 반드시 해제하고 한 번 시도합니다
-- (docs/30 시나리오 B: 해제 → 반경 밖 거절 → 지정 → 통과).
-- UPDATE members SET test_account = FALSE WHERE id IN (:a, :b);
