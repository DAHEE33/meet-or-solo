-- =============================================================================
-- WBS 10-B 점수 분해 저장 검증 스크립트 (V24 이후)
--
-- 목적
--   match_attempt_members의 jaccard_score / cosine_score / embedding_applied /
--   embedding_pair_count가 올바르게 저장됐는지 조회만으로 검산한다.
--
-- verify-embedding-score.sql과의 차이
--   verify-embedding-score.sql은 V24 이전 스크립트라 "임베딩이 점수에 반영되는가"를
--   임베딩 상태를 뒤집어 가며 2라운드로 실측 비교한다(데이터를 쓴다).
--   이 스크립트는 V24가 저장해 둔 분해 컬럼만 읽어 같은 판정을 한 번의 조회로 끝낸다.
--   따라서 데이터를 전혀 쓰지 않는다.
--
-- 판별 원리 (4-4절 저장 정의)
--   cosine_score IS NULL     -> member_score = jaccard_score
--   cosine_score IS NOT NULL -> member_score = jw * jaccard_score + ew * cosine_score
--   위 항등식은 모든 인원수·모든 혼합 조합에서 성립한다.
--   2인은 pair가 1개라 정확히 일치하고, 3~4인은 pair별 반올림 누적으로 0.01까지 어긋날 수 있다.
--
-- 실행 방법 (dev DB, SSH tunnel 기준)
--   scripts/start-dev-db-tunnel.ps1로 터널을 올린 뒤 psql로 접속해 실행한다.
--   전부 SELECT이므로 한 번에 실행해도 된다.
--
-- 주의
--   - 조회 전용이다. dev DB는 공유 자원이므로 이 스크립트에 INSERT/UPDATE/DELETE를 추가하지 않는다.
--   - jw / ew는 현재 backend에 적용된 .env의 MATCHING_SCORING_JACCARD_WEIGHT /
--     MATCHING_SCORING_EMBEDDING_WEIGHT와 같은 값으로 맞춘다. 기대값 검산에만 쓴다.
--   - V24 이전에 생성된 row는 분해값이 전부 NULL이다. 백필하지 않기로 한 의도된 상태이므로
--     4번 질의에서 "분해 저장 도입 이전"으로 분류된다.
-- =============================================================================

-- 파라미터. 다른 조합으로 재현하려면 이 값들만 바꾼다.
\set attempt_id 38
\set member_a 1
\set member_b 2
\set jw 0.50
\set ew 0.50


-- =============================================================================
-- 1. 대상 attempt 개요
--    target_group_size - 1이 회원당 pair 수이고, embedding_pair_count의 분모다.
-- =============================================================================

SELECT a.id AS attempt_id, a.festival_id, a.target_group_size,
       a.target_group_size - 1 AS pairs_per_member,
       a.status, a.score AS group_score, a.created_by,
       a.started_at, a.failed_reason
FROM match_attempts a
WHERE a.id = :attempt_id;


-- =============================================================================
-- 2. 분해값 검산 (이 스크립트의 본체)
--
--    rebuilt_score            저장된 분해값과 가중치로 재구성한 member_score
--    diff                     저장값 - 재구성값. 2인은 0.00, 3~4인은 |diff| <= 0.01
--                             재구성값을 먼저 소수 둘째 자리로 반올림한 뒤 뺀다. backend가
--                             pair 점수를 반올림해 저장하므로 반올림 전 값과 비교하면
--                             정확히 맞는 행도 0.01로 보인다.
--    diff_vs_group            member_score - match_attempts.score
--                             그룹 점수와 회원 점수가 갈라지지 않는지 확인한다(3절 원칙)
--    derived_embedding_weight (member_score - jaccard) / (cosine - jaccard)로 역산한 가중치
--                             4-4절이 가중치 컬럼을 두지 않은 근거다. 저장값이 이미 소수 둘째
--                             자리로 반올림돼 있어 역산 결과는 근사값이다(예: 0.4998).
--                             0.50/0.30 수준의 구분에는 충분하다.
--                             cosine_score IS NULL이거나 cosine = jaccard인 퇴화 케이스는 NULL
--    breakdown_shape          4-4절 3분류. 혼합이면 cosine_score가 합성값이므로 오독 주의
-- =============================================================================

SELECT
  m.attempt_id,
  m.member_id,
  mb.nickname,
  m.status,
  m.member_score,
  m.jaccard_score,
  m.cosine_score,
  m.embedding_applied,
  m.embedding_pair_count,
  a.score AS group_score,
  round(:jw * m.jaccard_score
        + :ew * coalesce(m.cosine_score, m.jaccard_score), 2) AS rebuilt_score,
  m.member_score
    - round(:jw * m.jaccard_score
            + :ew * coalesce(m.cosine_score, m.jaccard_score), 2) AS diff,
  round(m.member_score - a.score, 2) AS diff_vs_group,
  CASE
    WHEN m.cosine_score IS NULL OR m.cosine_score = m.jaccard_score THEN NULL
    ELSE round((m.member_score - m.jaccard_score) / (m.cosine_score - m.jaccard_score), 4)
  END AS derived_embedding_weight,
  CASE
    WHEN m.jaccard_score IS NULL THEN '분해 저장 도입 이전(V24 이전)'
    WHEN m.embedding_pair_count = 0 THEN '완전 fallback'
    WHEN m.embedding_pair_count = a.target_group_size - 1 THEN '완전 임베딩(순수 비교군)'
    ELSE '혼합(cosine_score 오독 주의)'
  END AS breakdown_shape
FROM match_attempt_members m
JOIN match_attempts a ON a.id = m.attempt_id
JOIN members mb ON mb.id = m.member_id
WHERE m.attempt_id = :attempt_id
ORDER BY m.member_id;


-- =============================================================================
-- 3. 같은 회원쌍의 라운드 비교
--    같은 두 회원이 여러 번 매칭됐을 때 분해값이 어떻게 달라지는지 시계열로 본다.
--    checkin_id가 같으면 체크인·태그가 고정된 상태에서 임베딩만 바뀐 단일 변수 비교가 된다.
-- =============================================================================

SELECT
  m.attempt_id,
  a.started_at,
  m.member_id,
  p.checkin_id,
  m.member_score,
  m.jaccard_score,
  m.cosine_score,
  m.embedding_applied,
  m.embedding_pair_count
FROM match_attempt_members m
JOIN match_attempts a ON a.id = m.attempt_id
JOIN match_pools p ON p.id = m.pool_id
WHERE m.attempt_id IN (
  SELECT x.attempt_id
  FROM match_attempt_members x
  WHERE x.member_id IN (:member_a, :member_b)
  GROUP BY x.attempt_id
  HAVING count(DISTINCT x.member_id) = 2
)
ORDER BY m.attempt_id DESC, m.member_id;


-- =============================================================================
-- 4. 임베딩 반영 여부를 저장된 컬럼만으로 판별
--    4-3절은 member_preference_embeddings.created_at을 따로 뒤져야 판별할 수 있었다.
--    V24 이후에는 이 질의 하나로 끝난다. member_preference_embeddings를 join하지 않는다.
-- =============================================================================

SELECT
  CASE
    WHEN m.jaccard_score IS NULL THEN '판별 불가 — 분해 저장 도입 이전(V24 이전)'
    WHEN m.embedding_applied THEN '임베딩 반영됨'
    ELSE '임베딩 미반영 — Jaccard 단독'
  END AS verdict,
  count(*) AS member_rows,
  min(m.attempt_id) AS first_attempt,
  max(m.attempt_id) AS last_attempt
FROM match_attempt_members m
GROUP BY 1
ORDER BY 1;


-- =============================================================================
-- 5. 코사인 baseline 분포
--    text-embedding-3-small은 무관한 짧은 한국어 문장끼리도 값이 크게 내려가지 않는다.
--    실측 baseline을 모르면 cosine_score를 절대값으로 오독하게 되므로 함께 잰다.
--    가중치를 조정할 때 이 분포의 하한과 폭을 근거로 삼는다.
-- =============================================================================

SELECT
  a.member_id AS member_a,
  b.member_id AS member_b,
  round((1 - (a.embedding <=> b.embedding))::numeric * 100, 2) AS cosine_x100,
  length(a.preference_text) AS text_len_a,
  length(b.preference_text) AS text_len_b
FROM member_preference_embeddings a
JOIN member_preference_embeddings b ON a.member_id < b.member_id
WHERE a.embedding_status = 'COMPLETED'
  AND b.embedding_status = 'COMPLETED'
ORDER BY cosine_x100;

SELECT
  count(*) AS pair_count,
  round(min(c.cosine_x100), 2) AS cosine_min,
  round(avg(c.cosine_x100), 2) AS cosine_avg,
  round(max(c.cosine_x100), 2) AS cosine_max,
  round(max(c.cosine_x100) - min(c.cosine_x100), 2) AS cosine_spread
FROM (
  SELECT (1 - (a.embedding <=> b.embedding))::numeric * 100 AS cosine_x100
  FROM member_preference_embeddings a
  JOIN member_preference_embeddings b ON a.member_id < b.member_id
  WHERE a.embedding_status = 'COMPLETED'
    AND b.embedding_status = 'COMPLETED'
) c;


-- =============================================================================
-- 6. 태그 Jaccard 교차 확인
--    2절의 jaccard_score가 맞는지 태그 원본에서 다시 계산해 대조한다.
--    2인 전용이다. 3인 이상은 pair 평균이므로 이 값과 직접 비교하지 않는다.
-- =============================================================================

WITH tags AS (
  SELECT member_id, array_agg(style_code ORDER BY style_code) AS codes
  FROM member_travel_styles
  WHERE member_id IN (:member_a, :member_b)
  GROUP BY member_id
)
SELECT
  a.member_id AS member_a, a.codes AS tags_a,
  b.member_id AS member_b, b.codes AS tags_b,
  cardinality(ARRAY(SELECT unnest(a.codes) INTERSECT SELECT unnest(b.codes))) AS intersect_size,
  cardinality(ARRAY(SELECT unnest(a.codes) UNION SELECT unnest(b.codes))) AS union_size,
  round(
    100.0 * cardinality(ARRAY(SELECT unnest(a.codes) INTERSECT SELECT unnest(b.codes)))
    / nullif(cardinality(ARRAY(SELECT unnest(a.codes) UNION SELECT unnest(b.codes))), 0), 2
  ) AS expected_jaccard
FROM tags a, tags b
WHERE a.member_id = :member_a AND b.member_id = :member_b;
