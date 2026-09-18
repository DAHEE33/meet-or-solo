package com.survey.meetorsolo.external.openai;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiEmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingConfig.class);

    @Bean
    public OpenAiEmbeddingClient openAiEmbeddingClient(OpenAiProperties properties) {
        String apiKey = sanitizeApiKey(properties.apiKey());
        RestClient restClient = createRestClient(properties, apiKey);
        return new OpenAiEmbeddingClient(restClient, properties.model(), apiKey != null);
    }

    /**
     * 주입된 API key에서 앞뒤 공백과 줄바꿈을 걷어내고, 값이 쓸 수 없는 상태면 기동 시점에 남긴다.
     *
     * <p>기동을 실패시키지 않는다. "임베딩 실패가 서비스를 막지 않는다"가 이 기능의 원칙이라
     * 키가 없어도 나머지 서비스는 그대로 떠야 한다. 대신 아무 신호 없이 401만 반복되던 상태를
     * 없애려고 경고를 남긴다.
     *
     * <p>공백 제거가 편의 처리가 아니다. Windows에서 편집한 {@code .env}를 그대로 서버에 옮기면
     * 값 끝에 {@code CR}이 붙어 {@code Bearer sk-...\r}가 되고, HTTP client가 헤더 자체를 거절해
     * 인증 실패와 구분되지 않는 예외가 난다. 키를 "제대로 넣었는데 실패하는" 대표 경로다.
     *
     * <p>키 값은 어떤 형태로도 로그에 남기지 않는다. 길이만 남긴다.
     */
    private String sanitizeApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            log.warn("OPENAI_API_KEY가 비어 있습니다. 취향 임베딩은 항상 {}로 실패합니다.",
                    EmbeddingFailureReason.API_KEY_MISSING);
            return null;
        }
        String apiKey = rawApiKey.strip();
        if (apiKey.length() != rawApiKey.length()) {
            log.warn("OPENAI_API_KEY 앞뒤에 공백 또는 줄바꿈이 있어 제거했습니다. 환경변수 파일의 줄 끝(CRLF)을 확인해 주세요.");
        }
        if (!apiKey.startsWith("sk-")) {
            log.warn("OPENAI_API_KEY 형식이 예상과 다릅니다. length={}", apiKey.length());
        }
        return apiKey;
    }

    private RestClient createRestClient(OpenAiProperties properties, String apiKey) {
        Duration connectTimeout = properties.connectTimeout() != null
                ? properties.connectTimeout() : Duration.ofSeconds(5);
        Duration readTimeout = properties.readTimeout() != null
                ? properties.readTimeout() : Duration.ofSeconds(10);
        String baseUrl = properties.baseUrl() != null
                ? properties.baseUrl() : "https://api.openai.com";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl);

        if (apiKey != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        return builder.build();
    }
}
