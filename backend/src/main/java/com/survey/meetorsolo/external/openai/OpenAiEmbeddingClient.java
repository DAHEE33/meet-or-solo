package com.survey.meetorsolo.external.openai;

import com.survey.meetorsolo.external.openai.dto.OpenAiEmbeddingRequest;
import com.survey.meetorsolo.external.openai.dto.OpenAiEmbeddingResponse;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class OpenAiEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);
    private static final int EXPECTED_DIMENSIONS = 1536;

    private final RestClient restClient;
    private final String model;
    private final boolean apiKeyPresent;

    OpenAiEmbeddingClient(RestClient restClient, String model, boolean apiKeyPresent) {
        this.restClient = restClient;
        this.model = model;
        this.apiKeyPresent = apiKeyPresent;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "임베딩할 텍스트가 비어있습니다.");
        }
        // 키가 없으면 요청을 보내봐야 401이다. 보내지 않고 이유를 확정한다.
        if (!apiKeyPresent) {
            log.warn("OpenAI Embedding 호출 생략. reason={}", EmbeddingFailureReason.API_KEY_MISSING);
            throw new EmbeddingFailedException(EmbeddingFailureReason.API_KEY_MISSING);
        }
        try {
            OpenAiEmbeddingResponse response = restClient.post()
                    .uri("/v1/embeddings")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(new OpenAiEmbeddingRequest(model, text))
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);

            return extractEmbedding(response);
        } catch (RestClientResponseException exception) {
            EmbeddingFailureReason reason = statusReason(exception.getStatusCode());
            log.warn("OpenAI Embedding API 실패. reason={}, status={}", reason, exception.getStatusCode());
            throw new EmbeddingFailedException(reason);
        } catch (RestClientException exception) {
            EmbeddingFailureReason reason = transportReason(exception);
            // 원인 예외 타입까지 남긴다. CONNECT_FAILED 하나에 DNS·아웃바운드 차단·TLS 실패가
            // 모두 모이므로, 어느 쪽인지는 예외 타입과 메시지로만 갈린다.
            log.warn("OpenAI Embedding API 실패. reason={}, cause={}, message={}",
                    reason, rootCauseType(exception), rootCauseMessage(exception));
            throw new EmbeddingFailedException(reason);
        }
    }

    public String getModel() {
        return model;
    }

    /** API key가 설정돼 있는지. 진단용 조회에서 쓴다. */
    public boolean isApiKeyPresent() {
        return apiKeyPresent;
    }

    private EmbeddingFailureReason statusReason(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) return EmbeddingFailureReason.UNAUTHORIZED;
        if (status.value() == 429) return EmbeddingFailureReason.RATE_LIMITED;
        if (status.is5xxServerError()) return EmbeddingFailureReason.UPSTREAM_ERROR;
        if (status.is4xxClientError()) return EmbeddingFailureReason.INVALID_REQUEST;
        return EmbeddingFailureReason.UNKNOWN;
    }

    private EmbeddingFailureReason transportReason(RestClientException exception) {
        if (exception instanceof ResourceAccessException) {
            Throwable cause = exception.getCause();
            if (cause instanceof SocketTimeoutException) return EmbeddingFailureReason.TIMEOUT;
            if (cause instanceof IOException) return EmbeddingFailureReason.CONNECT_FAILED;
            return EmbeddingFailureReason.CONNECT_FAILED;
        }
        return EmbeddingFailureReason.INVALID_RESPONSE;
    }

    private String rootCauseType(Throwable exception) {
        Throwable cause = exception.getCause();
        return cause == null ? exception.getClass().getSimpleName() : cause.getClass().getSimpleName();
    }

    /**
     * 로그에 남길 원인 메시지. 취향 원문과 API key는 여기에 실리지 않는다 — 전송 계층 예외의
     * 메시지에는 대상 URL과 실패 사유만 담긴다.
     */
    private String rootCauseMessage(Throwable exception) {
        Throwable cause = exception.getCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        if (message == null) return "";
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private float[] extractEmbedding(OpenAiEmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            log.warn("OpenAI Embedding API 응답이 비어있습니다. reason={}", EmbeddingFailureReason.INVALID_RESPONSE);
            throw new EmbeddingFailedException(EmbeddingFailureReason.INVALID_RESPONSE);
        }
        List<Float> values = response.data().get(0).embedding();
        if (values == null || values.size() != EXPECTED_DIMENSIONS) {
            log.warn("OpenAI Embedding 차원 불일치. reason={}, expected={}, actual={}",
                    EmbeddingFailureReason.INVALID_RESPONSE, EXPECTED_DIMENSIONS, values == null ? 0 : values.size());
            throw new EmbeddingFailedException(EmbeddingFailureReason.INVALID_RESPONSE);
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
