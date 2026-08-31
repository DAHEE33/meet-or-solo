package com.survey.meetorsolo.domain.festival.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.festival.detail-info")
public record FestivalDetailInfoProperties(Duration cacheTtl) {

    public FestivalDetailInfoProperties {
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("축제 부가 정보 캐시 TTL은 0보다 커야 합니다.");
        }
    }
}
