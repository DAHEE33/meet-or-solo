package com.survey.meetorsolo.external.openai;

/**
 * 임베딩 생성이 실패한 이유.
 *
 * <p>실패는 서비스를 막지 않고 조용히 {@code FAILED}로 기록되므로, 이유를 남기지 않으면 운영
 * 중에 "왜 안 되는지"를 알 방법이 없다. 로컬에서는 되는데 dev 서버에서만 실패할 때 키 문제인지,
 * 아웃바운드 차단인지, 응답 형식 문제인지 구분하려고 둔 값이다.
 *
 * <p>회원 응답에는 내려보내지 않는다. 회원에게는 "분석 실패"로 충분하고, 이 값은 로그와 DB
 * ({@code member_preference_embeddings.embedding_error_reason})에서 운영자가 본다.
 */
public enum EmbeddingFailureReason {

    /** API key가 비어 있다. 요청을 보내기 전에 판정한다. */
    API_KEY_MISSING,

    /** 401/403. key가 틀렸거나 권한이 없거나 호출이 차단됐다. */
    UNAUTHORIZED,

    /** 429. 사용량 한도. */
    RATE_LIMITED,

    /** 4xx 중 위에 해당하지 않는 것. 요청 자체가 거절됐다. */
    INVALID_REQUEST,

    /** 5xx. 상대 서버 문제다. */
    UPSTREAM_ERROR,

    /** 응답 시간 초과. */
    TIMEOUT,

    /** 연결 자체를 못 했다. DNS, 아웃바운드 차단, TLS 실패가 여기로 모인다. */
    CONNECT_FAILED,

    /** 응답은 왔지만 형식이나 차원이 기대와 다르다. */
    INVALID_RESPONSE,

    /** 위 어디에도 들어가지 않은 예외. 분류를 넓히기 전까지의 기본값이다. */
    UNKNOWN;

    /** DB 컬럼 길이({@code VARCHAR(40)})를 넘지 않는지 확인하는 상수. */
    public static final int MAX_NAME_LENGTH = 40;
}
