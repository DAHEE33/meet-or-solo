package com.survey.meetorsolo.domain.tourplace.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tour-place.sync")
public record TourPlaceSyncProperties(
        boolean enabled,
        Duration initialDelay,
        Duration fixedDelay,
        int pageSize,
        int maxPages,
        String regionCode,
        List<String> contentTypeIds,
        int batchSize,
        int retryMaxAttempts,
        Duration retryInitialDelay,
        Duration retryMaxDelay
) {

    private static final List<String> ALLOWED_CONTENT_TYPE_IDS = List.of("12", "14", "28", "39");

    public TourPlaceSyncProperties {
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("관광지 동기화 최초 대기 시간은 0 이상이어야 합니다.");
        }
        if (fixedDelay == null || fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("관광지 동기화 반복 주기는 0보다 커야 합니다.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("관광지 동기화 페이지 크기는 1 이상이어야 합니다.");
        }
        if (maxPages < 1) {
            throw new IllegalArgumentException("관광지 동기화 최대 페이지 수는 1 이상이어야 합니다.");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("관광지 동기화 배치 크기는 1 이상이어야 합니다.");
        }
        if (retryMaxAttempts < 1 || retryMaxAttempts > 10) {
            throw new IllegalArgumentException("관광지 동기화 최대 시도 횟수는 1~10이어야 합니다.");
        }
        if (retryInitialDelay == null || retryInitialDelay.isNegative()) {
            throw new IllegalArgumentException("관광지 동기화 재시도 최초 대기 시간은 0 이상이어야 합니다.");
        }
        if (retryMaxDelay == null || retryMaxDelay.isNegative()
                || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException("관광지 동기화 재시도 최대 대기 시간은 최초 대기 시간 이상이어야 합니다.");
        }
        regionCode = required(regionCode, "관광지 동기화 시도 코드");
        if (contentTypeIds == null || contentTypeIds.isEmpty()) {
            throw new IllegalArgumentException("관광지 동기화 콘텐츠 타입 ID 목록은 필수입니다.");
        }
        contentTypeIds = contentTypeIds.stream().map(String::trim).distinct().toList();
        for (String contentTypeId : contentTypeIds) {
            if (!ALLOWED_CONTENT_TYPE_IDS.contains(contentTypeId)) {
                throw new IllegalArgumentException(
                        "관광지 동기화 콘텐츠 타입 ID는 " + ALLOWED_CONTENT_TYPE_IDS + " 중 하나여야 합니다: " + contentTypeId
                );
            }
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
        return value.trim();
    }
}
