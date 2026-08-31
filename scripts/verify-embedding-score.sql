-- =============================================================================
-- WBS 10-B 임베딩 점수 실측 검증 스크립트
--
-- 목적
--   "취향 임베딩이 실제 매칭 점수(match_attempt_members.member_score)에 반영되는가"를
--   2인 매칭 2회(양쪽 취향 보유 / 한쪽만 보유)로 실측 비교한다.
--
-- 판별 원리
--   PairCompatibilityScorer.score()는 양쪽 모두 COMPLETED 임베딩을 가진 경우에만
--   jaccard * jaccard-weight + cosine * embedding-weight 로 가중 합산하고,
--   한쪽이라도 없으면 Jaccard 점수만 사용한다.
--   따라서 취향 태그를 고정한 채 임베딩 가용 여부만 바꾸면 점수 차이가 곧 임베딩 반영 여부다.
--
-- 측정 대상이 왜 제안 생성 시점인가
--   member_score는 MatchProposalCreationService.createInitial()에서 proposal을 만들 때
--   확정 저장되고 이후 수락/거절/타임아웃으로 바뀌지 않는다. 따라서 제안이 뜨는 것까지만
--   가면 측정이 끝나고, 응답할 필요가 없다.
--
-- 실행 방법 (dev DB, SSH tunnel 기준)
--   scripts/start-dev-db-tunnel.ps1 로 터널을 올린 뒤 psql로 접속해 단계별로 실행한다.
--   전체를 한 번에 실행하지 않는다. 라운드 사이에 브라우저 조작과 대기가 필요하다.
--
-- 주의
--   - 이 스크립트는 검증 데이터를 쓴다. 운영 DB에서 실행하지 않는다.
--   - 6단계 원복(RESTORE)을 반드시 수행한다. 건너뛰면 회원 B의 취향이 매칭에서 계속 무시된다.
--   - 가중치(.env의 MATCHING_SCORING_*)는 읽기만 하고 수정하지 않는다.
-- =============================================================================

-- 파라미터. 다른 회원/축제로 재현하려면 이 값들만 바꾼다.
-- jw / ew는 현재 backend에 적용된 .env의 MATCHING_SCORING_JACCARD_WEIGHT /
-- MATCHING_SCORING_EMBEDDING_WEIGHT와 같은 값으로 맞춘다. 기대값 계산에만 쓰인다.
\set member_a 2
\set member_b 27
\set festival 144
\set jw 0.50
\set ew 0.50


-- =============================================================================
-- 0. 사전 점검
--    아래 쿼리가 모두 기대대로 나와야 매칭이 성사된다. 하나라도 어긋나면 원인을 먼저 해소한다.
-- =============================================================================

-- 0-1. 두 회원이 ACTIVE인가. 축제가 ACTIVE인가.
SELECT 'member' AS kind, id::text AS id, nickname AS name, status
FROM members WHERE id IN (:member_a, :member_b)
UNION ALL
SELECT 'festival', id::text, title, status
FROM festivals WHERE id = :festival
ORDER BY kind DESC, id;

-- 0-2. 축제에 ACTIVE 만남 장소가 있는가.
--      없으면 매칭 신청이 MATCHING_MEETING_POINT_NOT_READY로 거절된다.
SELECT id, name, status, assignment_order
FROM festival_meeting_points
WHERE festival_id = :festival AND status = 'ACTIVE'
ORDER BY assignment_order;

-- 0-3. 진행 중인 pool / 활성 그룹 / 활성 쿨타임이 없어야 한다. 세 쿼리 모두 0건이 정상.
SELECT id, member_id, status FROM match_pools
WHERE member_id IN (:member_a, :member_b) AND status IN ('WAITING', 'LOCKED', 'PROPOSED');

SELECT gm.id, gm.member_id, gm.status, g.status AS group_status
FROM match_group_members gm JOIN match_groups g ON g.id = gm.group_id
WHERE gm.member_id IN (:member_a, :member_b) AND gm.status = 'JOINED' AND g.status = 'CONFIRMED';

SELECT id, member_id, reason, expires_at FROM match_cooldowns
WHERE member_id IN (:member_a, :member_b)
  AND status = 'ACTIVE' AND starts_at <= now() AND expires_at > now();

-- 0-4. 취향 태그와 임베딩 상태. 두 회원 모두 COMPLETED여야 라운드 A를 시작할 수 있다.
SELECT m.id AS member_id,
       (SELECT string_agg(s.style_code, ',' ORDER BY s.style_code)
          FROM member_travel_styles s WHERE s.member_id = m.id) AS travel_styles,
       e.embedding_status,
       e.embedding_model,
       (e.embedding IS NOT NULL) AS has_vector
FROM members m
LEFT JOIN member_preference_embeddings e ON e.member_id = m.id
WHERE m.id IN (:member_a, :member_b)
ORDER BY m.id;

-- 0-5. 기대값 미리 계산.
--      jaccard : 태그 교집합 / 합집합 * 100
--      cosine  : pgvector 코사인 유사도 * 100 (1 - (a <=> b))
--      라운드 A 기대 점수는 두 값의 가중 합, 라운드 B 기대 점수는 jaccard 단독이다.
WITH styles AS (
    SELECT
        (SELECT array_agg(style_code) FROM member_travel_styles WHERE member_id = :member_a) AS a,
        (SELECT array_agg(style_code) FROM member_travel_styles WHERE member_id = :member_b) AS b
),
jaccard AS (
    SELECT round(
        cardinality(ARRAY(SELECT unnest(a) INTERSECT SELECT unnest(b)))::numeric * 100
        / nullif(cardinality(ARRAY(SELECT unnest(a) UNION SELECT unnest(b))), 0), 2) AS value
    FROM styles
),
cosine AS (
    SELECT round(((1 - (ea.embedding <=> eb.embedding)) * 100)::numeric, 2) AS value
    FROM member_preference_embeddings ea, member_preference_embeddings eb
    WHERE ea.member_id = :member_a AND eb.member_id = :member_b
)
SELECT
    jaccard.value AS jaccard,
    cosine.value  AS cosine,
    round(jaccard.value * :jw + cosine.value * :ew, 2) AS expected_round_a,
    jaccard.value AS expected_round_b_jaccard_only
FROM jaccard, cosine;


-- =============================================================================
-- 1. 체크인 시딩
--    체크인 API가 아직 없으므로 SQL로 넣는다. 유효시간은 1시간이다.
--    uq_festival_checkins_member_festival_active 때문에 기존 ACTIVE를 먼저 내려야 한다.
--    라운드마다 다시 실행한다. 새 checkin_id를 받으면 match_opponent_exclusions도 함께 리셋된다.
-- =============================================================================
BEGIN;

UPDATE festival_checkins
SET status = 'EXPIRED', updated_at = now()
WHERE member_id IN (:member_a, :member_b)
  AND festival_id = :festival
  AND status = 'ACTIVE';

INSERT INTO festival_checkins (member_id, festival_id, distance_meters, status, checked_in_at, expires_at)
VALUES
    (:member_a, :festival, 10, 'ACTIVE', now(), now() + INTERVAL '1 hour'),
    (:member_b, :festival, 10, 'ACTIVE', now(), now() + INTERVAL '1 hour')
RETURNING id, member_id, checked_in_at, expires_at;

COMMIT;

-- 1-1. 새 체크인 조합에 상대 제외 기록이 없는지 확인. 0건이 정상.
WITH active_checkins AS (
    SELECT id FROM festival_checkins
    WHERE member_id IN (:member_a, :member_b) AND festival_id = :festival AND status = 'ACTIVE'
)
SELECT * FROM match_opponent_exclusions
WHERE lower_checkin_id IN (SELECT id FROM active_checkins)
  AND higher_checkin_id IN (SELECT id FROM active_checkins);


-- =============================================================================
-- 2. 라운드 A — 양쪽 취향 보유
--    브라우저에서 두 회원이 각각 "자동 매칭 신청"을 누른다. 탐색 시간이 60초이므로
--    두 신청이 60초 안에 겹쳐야 한다. 제안 화면이 뜨면 아무것도 누르지 않는다.
--    응답하지 않고 두면 30초 뒤 타임아웃되고, 명시적 거절이 아니라서 상대 제외가 생기지 않는다.
--    제안이 뜬 뒤 아래 3단계 측정 쿼리를 실행한다.
-- =============================================================================


-- =============================================================================
-- 3. 측정
--    저장된 member_score와 DB에서 다시 계산한 기대값을 한 행에 나란히 출력한다.
--    diff_a / diff_b가 0.00이면 계산식이 일치한 것이다.
--    (float4 벡터 연산과 Java double 연산의 차이로 반올림 경계에서 ±0.01은 허용한다.)
--
--    반드시 해당 라운드의 임베딩 상태 그대로일 때 실행한다.
--    기대값은 "실행 시점"의 embedding_status로 다시 계산하는데 member_score는 매칭 시점에 저장된
--    값이다. 4단계 상태 전환이나 6단계 원복을 한 뒤에 이 쿼리를 돌리면 두 시점이 어긋나 diff가
--    효과 크기(= |라운드 A 점수 - 라운드 B 점수|)만큼 벌어진 채로 나온다. 계산식 불일치가 아니다.
-- =============================================================================
WITH latest AS (
    SELECT a.id, a.status, a.score, a.target_group_size, a.created_by, a.started_at
    FROM match_attempts a
    WHERE a.festival_id = :festival
      AND EXISTS (SELECT 1 FROM match_attempt_members m
                  WHERE m.attempt_id = a.id AND m.member_id = :member_a)
      AND EXISTS (SELECT 1 FROM match_attempt_members m
                  WHERE m.attempt_id = a.id AND m.member_id = :member_b)
    ORDER BY a.id DESC
    LIMIT 1
),
scores AS (
    SELECT
        max(CASE WHEN m.member_id = :member_a THEN m.member_score END) AS score_a,
        max(CASE WHEN m.member_id = :member_b THEN m.member_score END) AS score_b
    FROM match_attempt_members m JOIN latest ON latest.id = m.attempt_id
),
styles AS (
    SELECT
        (SELECT array_agg(style_code) FROM member_travel_styles WHERE member_id = :member_a) AS a,
        (SELECT array_agg(style_code) FROM member_travel_styles WHERE member_id = :member_b) AS b
),
jaccard AS (
    SELECT round(
        cardinality(ARRAY(SELECT unnest(a) INTERSECT SELECT unnest(b)))::numeric * 100
        / nullif(cardinality(ARRAY(SELECT unnest(a) UNION SELECT unnest(b))), 0), 2) AS value
    FROM styles
),
-- MatchingBatchReader와 같은 조건으로 COMPLETED 임베딩만 본다.
-- 한쪽이라도 COMPLETED가 아니면 이 CTE가 0행이 되고 cosine은 NULL이 된다. 그것이 fallback 상태다.
cosine AS (
    SELECT round(((1 - (ea.embedding <=> eb.embedding)) * 100)::numeric, 2) AS value
    FROM member_preference_embeddings ea, member_preference_embeddings eb
    WHERE ea.member_id = :member_a AND ea.embedding_status = 'COMPLETED'
      AND eb.member_id = :member_b AND eb.embedding_status = 'COMPLETED'
),
expected AS (
    SELECT
        jaccard.value AS jaccard,
        (SELECT value FROM cosine) AS cosine,
        CASE WHEN (SELECT value FROM cosine) IS NULL
             THEN jaccard.value
             ELSE round(jaccard.value * :jw + (SELECT value FROM cosine) * :ew, 2)
        END AS expected_score
    FROM jaccard
)
SELECT
    latest.id      AS attempt_id,
    latest.status  AS attempt_status,
    latest.created_by,
    latest.started_at,
    latest.score   AS attempt_score,
    scores.score_a AS stored_score_member_a,
    scores.score_b AS stored_score_member_b,
    expected.jaccard,
    expected.cosine,
    CASE WHEN expected.cosine IS NULL THEN 'JACCARD_ONLY' ELSE 'JACCARD+EMBEDDING' END AS mode,
    expected.expected_score,
    scores.score_a - expected.expected_score AS diff_a,
    scores.score_b - expected.expected_score AS diff_b
FROM latest, scores, expected;


-- =============================================================================
-- 4. 라운드 B 준비 — 회원 B의 임베딩만 비활성화
--    MatchingBatchReader는 embedding_status = 'COMPLETED'인 임베딩만 읽는다.
--    벡터와 원문은 지우지 않고 상태만 FAILED로 뒤집어 "한쪽만 보유" 상황을 만든다.
--    되돌릴 수 있고 취향 원문이 유실되지 않는다.
-- =============================================================================
UPDATE member_preference_embeddings
SET embedding_status = 'FAILED', updated_at = now()
WHERE member_id = :member_b
RETURNING member_id, embedding_status, (embedding IS NOT NULL) AS has_vector;


-- =============================================================================
-- 5. 라운드 B — 한쪽만 취향 보유
--    라운드 A의 타임아웃으로 한쪽에 2분 쿨타임이 붙는다. 0-3의 쿨타임 쿼리가 0건이 될 때까지 기다린다.
--    1단계 체크인 시딩을 다시 실행한 뒤 브라우저에서 두 회원이 다시 신청하고,
--    3단계 측정 쿼리를 다시 실행한다.
--    이때 cosine 컬럼은 NULL, mode는 JACCARD_ONLY로 나오는 것이 정상이다.
-- =============================================================================


-- =============================================================================
-- 6. 원복 (RESTORE) — 반드시 실행한다
--    원복 전에 3단계 측정 결과를 기록해 둔다. 원복 후에는 기대값이 다시 계산되므로
--    같은 쿼리로 라운드 B 결과를 재현할 수 없다.
-- =============================================================================
UPDATE member_preference_embeddings
SET embedding_status = 'COMPLETED', updated_at = now()
WHERE member_id = :member_b
  AND embedding IS NOT NULL
  AND embedding_model IS NOT NULL
RETURNING member_id, embedding_status, embedding_model;

-- 6-1. 원복 확인. 두 회원 모두 COMPLETED여야 한다.
SELECT member_id, embedding_status, embedding_model, (embedding IS NOT NULL) AS has_vector
FROM member_preference_embeddings
WHERE member_id IN (:member_a, :member_b)
ORDER BY member_id;

-- 6-2. 검증용 체크인 정리. 남겨두면 1시간 동안 매칭풀에 들어갈 수 있다.
UPDATE festival_checkins
SET status = 'EXPIRED', updated_at = now()
WHERE member_id IN (:member_a, :member_b)
  AND festival_id = :festival
  AND status = 'ACTIVE'
RETURNING id, member_id, status;
