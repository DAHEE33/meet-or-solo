package com.survey.meetorsolo.domain.festival.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.festival.checkin")
public record FestivalCheckinProperties(
        int accuracyThresholdMeters,
        boolean bypassRadiusCheck
) {

    public FestivalCheckinProperties {
        if (accuracyThresholdMeters <= 0) {
            throw new IllegalArgumentException("위치 정확도 임계값은 0보다 커야 합니다.");
        }
    }
}
