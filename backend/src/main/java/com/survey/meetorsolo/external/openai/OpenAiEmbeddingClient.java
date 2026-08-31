package com.survey.meetorsolo.external.openai;

import com.survey.meetorsolo.external.openai.dto.OpenAiEmbeddingRequest;
import com.survey.meetorsolo.external.openai.dto.OpenAiEmbeddingResponse;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class OpenAiEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);
    private static final int EXPECTED_DIMENSIONS = 1536;

    private final RestClient restClient;
    private final String model;

    OpenAiEmbeddingClient(RestClient restClient, String model) {
        this.restClient = restClient;
        this.model = model;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "임베딩할 텍스트가 비어있습니다.");
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
            log.warn("OpenAI Embedding API 실패. status={}", exception.getStatusCode());
            throw new BusinessException(ErrorCode.EMBEDDING_API_FAILED);
        } catch (RestClientException exception) {
            log.warn("OpenAI Embedding API 실패. cause={}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EMBEDDING_API_FAILED);
        }
    }

    public String getModel() {
        return model;
    }

    private float[] extractEmbedding(OpenAiEmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            log.warn("OpenAI Embedding API 응답이 비어있습니다.");
            throw new BusinessException(ErrorCode.EMBEDDING_API_FAILED);
        }
        List<Float> values = response.data().get(0).embedding();
        if (values == null || values.size() != EXPECTED_DIMENSIONS) {
            log.warn("OpenAI Embedding 차원 불일치. expected={}, actual={}",
                    EXPECTED_DIMENSIONS, values == null ? 0 : values.size());
            throw new BusinessException(ErrorCode.EMBEDDING_API_FAILED);
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
