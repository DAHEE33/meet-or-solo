package com.survey.meetorsolo.domain.festival.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.festival.checkin")
public record FestivalCheckinProperties(Duration validDuration, int accuracyThresholdMeters) {

    public FestivalCheckinProperties {
        if (validDuration == null || validDuration.isZero() || validDuration.isNegative()) {
            throw new IllegalArgumentException("체크인 유효 기간은 0보다 커야 합니다.");
        }
        if (accuracyThresholdMeters <= 0) {
            throw new IllegalArgumentException("위치 정확도 임계값은 0보다 커야 합니다.");
        }
    }
}
