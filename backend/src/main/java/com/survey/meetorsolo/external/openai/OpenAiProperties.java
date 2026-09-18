package com.survey.meetorsolo.external.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout
) {
}
