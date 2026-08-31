package com.survey.meetorsolo.external.tourapi.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tour-api")
public record TourApiProperties(
        String baseUrl,
        String serviceKey,
        String mobileOs,
        String mobileApp,
        Duration connectTimeout,
        Duration readTimeout
) {
}
