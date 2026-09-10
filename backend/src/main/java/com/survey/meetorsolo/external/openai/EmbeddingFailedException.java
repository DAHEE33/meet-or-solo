package com.survey.meetorsolo.external.openai;

import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;

/**
 * 임베딩 생성 실패. 실패 이유를 함께 들고 다닌다.
 *
 * <p>{@link BusinessException}을 그대로 상속하므로 기존 예외 처리 흐름과 응답 코드
 * ({@code EMBEDDING_API_FAILED})는 바뀌지 않는다. 이유는 저장과 로그에만 쓴다.
 */
public class EmbeddingFailedException extends BusinessException {

    private final EmbeddingFailureReason reason;

    public EmbeddingFailedException(EmbeddingFailureReason reason) {
        super(ErrorCode.EMBEDDING_API_FAILED);
        this.reason = reason;
    }

    public EmbeddingFailureReason getReason() {
        return reason;
    }
}
