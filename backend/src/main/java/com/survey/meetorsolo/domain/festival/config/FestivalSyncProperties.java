package com.survey.meetorsolo.domain.festival.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.festival.sync")
public record FestivalSyncProperties(
        boolean enabled,
        Duration initialDelay,
        Duration fixedDelay,
        int pageSize,
        int maxPages,
        int lookbackDays,
        int lookaheadDays,
        String regionCode,
        String classificationSystem1,
        String classificationSystem2
) {

    public FestivalSyncProperties {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("축제 동기화 최초 대기 시간은 0 이상이어야 합니다.");
        }
        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("축제 동기화 반복 주기는 0보다 커야 합니다.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("축제 동기화 페이지 크기는 1 이상이어야 합니다.");
        }
        if (maxPages < 1) {
            throw new IllegalArgumentException("축제 동기화 최대 페이지 수는 1 이상이어야 합니다.");
        }
        if (lookbackDays < 0 || lookaheadDays < 0) {
            throw new IllegalArgumentException("축제 동기화 조회 기간은 0일 이상이어야 합니다.");
        }
        regionCode = required(regionCode, "축제 동기화 시도 코드");
        classificationSystem1 = required(classificationSystem1, "축제 동기화 대분류 코드");
        classificationSystem2 = required(classificationSystem2, "축제 동기화 중분류 코드");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
        return value.trim();
    }
}
