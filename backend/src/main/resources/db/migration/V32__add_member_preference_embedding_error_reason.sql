-- 취향 임베딩 실패 이유를 기록한다.
--
-- 지금까지는 실패가 embedding_status = 'FAILED' 한 값으로만 남아, 키 문제인지 아웃바운드
-- 차단인지 응답 형식 문제인지 DB만 봐서는 구분할 수 없었다. 로컬은 되는데 dev 서버에서만
-- 실패하는 상황에서 원인을 좁힐 수 없어 컬럼을 추가한다.
--
-- 값은 EmbeddingFailureReason enum 이름이다(API_KEY_MISSING, UNAUTHORIZED, RATE_LIMITED,
-- INVALID_REQUEST, UPSTREAM_ERROR, TIMEOUT, CONNECT_FAILED, INVALID_RESPONSE, UNKNOWN).
-- 값 집합이 코드에서 바뀔 수 있으므로 CHECK 제약으로 고정하지 않는다.
-- FAILED가 아닌 행에서는 항상 NULL이다.

ALTER TABLE member_preference_embeddings
    ADD COLUMN embedding_error_reason VARCHAR(40);

COMMENT ON COLUMN member_preference_embeddings.embedding_error_reason
    IS '임베딩 실패 이유(EmbeddingFailureReason). 실패 상태에서만 값이 있다.';
