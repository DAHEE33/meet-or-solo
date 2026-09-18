-- 매칭 회원 점수의 분해값 저장
--
-- 번호가 V22가 아니라 V24인 이유:
--   공유 dev DB에 다른 작업의 V22(add festival tourplace query indexes, 2026-08-26)와
--   V23(add tour place region codes, 2026-08-27)이 이미 적용돼 있다. 두 파일은 아직
--   origin/dev에 병합되지 않았지만 dev DB에는 반영된 상태라 V22를 잡으면 checksum
--   mismatch로 기동이 실패한다. 새 migration 번호는 저장소뿐 아니라 dev DB의
--   flyway_schema_history도 확인하고 정해야 한다.
--
-- 기존에는 총점(member_score) 하나만 남아 "임베딩이 실제로 매칭 품질을 높였는가"를 사후에
-- 판단하려면 그 시점의 태그 구성, 벡터, 가중치를 모두 다시 모아 재계산해야 했다. 임베딩은
-- 회원이 취향을 수정하면 덮어써지고 가중치는 환경변수라 시간이 지나면 역산이 불가능해진다.
--
-- 저장 정의 (3인 이상 혼합 pair 포함, docs/10_PROGRESS_LOG.md 4-4절):
--   jaccard_score        전체 pair의 Jaccard 평균
--   cosine_score         전체 pair의 "임베딩 항 투입값" 평균.
--                        임베딩 pair는 실제 코사인, fallback pair는 그 pair의 Jaccard를 투입값으로 본다
--                        (fallback = cosine 대신 jaccard를 쓰는 것과 수학적으로 같다는 기존 원칙).
--                        임베딩 pair가 하나도 없으면 NULL.
--   embedding_applied    임베딩 pair가 1개 이상인가. cosine_score IS NOT NULL과 동치다.
--   embedding_pair_count 실제 임베딩이 적용된 pair 수. 회원당 전체 pair 수는
--                        match_attempts.target_group_size - 1로 복원되므로 분모는 저장하지 않는다.
--
-- 이 정의에서 아래 항등식이 모든 인원수와 모든 혼합 조합에서 성립한다
-- (pair 단위 반올림 때문에 3~4인은 0.01 오차가 생길 수 있다).
--   cosine_score IS NULL     -> member_score = jaccard_score
--   cosine_score IS NOT NULL -> member_score = jaccard_weight * jaccard_score + embedding_weight * cosine_score
--
-- 가중치는 별도 컬럼으로 남기지 않는다. 세 점수로 행마다 역산할 수 있기 때문이다.

ALTER TABLE match_attempt_members ADD COLUMN jaccard_score NUMERIC(10,2);
ALTER TABLE match_attempt_members ADD COLUMN cosine_score NUMERIC(10,2);
ALTER TABLE match_attempt_members ADD COLUMN embedding_applied BOOLEAN;
ALTER TABLE match_attempt_members ADD COLUMN embedding_pair_count SMALLINT;

-- 기존 row는 백필하지 않는다. 그 시점의 벡터와 가중치를 복원할 수 없어 추정값을 넣으면
-- "임베딩이 실제로 쓰였는가"를 판정하려고 만든 컬럼이 오히려 거짓 근거가 된다.
-- NULL은 "점수 분해 저장 도입 이전 데이터"를 뜻한다.

-- 네 컬럼이 서로 어긋나지 않도록 동치 관계를 제약으로 강제한다.
ALTER TABLE match_attempt_members ADD CONSTRAINT chk_match_attempt_members_score_breakdown CHECK (
    (
        jaccard_score IS NULL
        AND cosine_score IS NULL
        AND embedding_applied IS NULL
        AND embedding_pair_count IS NULL
    )
    OR (
        jaccard_score IS NOT NULL
        AND embedding_applied IS NOT NULL
        AND embedding_pair_count IS NOT NULL
        AND jaccard_score BETWEEN 0 AND 100
        AND (cosine_score IS NULL OR cosine_score BETWEEN 0 AND 100)
        AND (cosine_score IS NOT NULL) = embedding_applied
        AND (embedding_pair_count > 0) = embedding_applied
    )
);
