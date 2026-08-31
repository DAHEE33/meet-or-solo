package com.survey.meetorsolo.external.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiEmbeddingConfig {

    @Bean
    public OpenAiEmbeddingClient openAiEmbeddingClient(OpenAiProperties properties) {
        RestClient restClient = createRestClient(properties);
        return new OpenAiEmbeddingClient(restClient, properties.model());
    }

    private RestClient createRestClient(OpenAiProperties properties) {
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

        String apiKey = properties.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        return builder.build();
    }
}
