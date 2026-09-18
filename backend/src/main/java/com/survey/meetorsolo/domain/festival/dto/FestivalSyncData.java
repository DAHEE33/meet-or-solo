package com.survey.meetorsolo.domain.festival.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record FestivalSyncData(
        String contentId,
        String contentTypeId,
        String title,
        String address,
        String regionCode,
        String sigunguCode,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        BigDecimal mapX,
        BigDecimal mapY,
        String originImageUrl,
        String thumbnailUrl,
        OffsetDateTime syncedAt,
        Map<String, Object> rawData
) {

    public FestivalSyncData {
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
