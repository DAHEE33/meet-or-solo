package com.survey.meetorsolo.domain.tourplace.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TourPlaceSyncData(
        String contentId,
        String contentTypeId,
        String title,
        String address,
        String regionCode,
        String sigunguCode,
        BigDecimal mapX,
        BigDecimal mapY,
        String tel,
        String imageUrl,
        OffsetDateTime syncedAt,
        Map<String, Object> rawData
) {

    public TourPlaceSyncData {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(contentTypeId, "contentTypeId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(syncedAt, "syncedAt");
        Map<String, Object> copiedRawData = rawData == null
                ? Map.of()
                : new LinkedHashMap<>(rawData);
        rawData = Collections.unmodifiableMap(copiedRawData);
    }
}
