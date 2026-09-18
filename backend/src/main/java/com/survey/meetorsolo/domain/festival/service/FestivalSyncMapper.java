package com.survey.meetorsolo.domain.festival.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FestivalSyncMapper {

    private static final String FESTIVAL_CONTENT_TYPE_ID = "15";
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final TypeReference<LinkedHashMap<String, Object>> RAW_DATA_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public FestivalSyncMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<FestivalSyncData> toSyncData(
            SearchFestivalItem item,
            OffsetDateTime syncedAt
    ) {
        if (item == null) {
            return Optional.empty();
        }

        String contentId = normalize(item.contentId());
        String contentTypeId = normalize(item.contentTypeId());
        String title = normalize(item.title());
        if (contentId == null || contentId.length() > 50
                || !FESTIVAL_CONTENT_TYPE_ID.equals(contentTypeId)
                || title == null) {
            return Optional.empty();
        }

        try {
            LocalDate eventStartDate = parseDate(item.eventStartDate());
            LocalDate eventEndDate = parseDate(item.eventEndDate());
            if (eventStartDate != null && eventEndDate != null
                    && eventEndDate.isBefore(eventStartDate)) {
                return Optional.empty();
            }

            String originImageUrl = normalizeImageUrl(item.firstImage());
            String thumbnailUrl = normalizeImageUrl(item.firstImageThumbnail());
            if (originImageUrl == null) {
                originImageUrl = thumbnailUrl;
            }

            return Optional.of(new FestivalSyncData(
                    contentId,
                    contentTypeId,
                    truncate(title, 255),
                    truncate(joinAddress(item.address1(), item.address2()), 500),
                    truncate(normalize(item.regionCode()), 20),
                    truncate(normalize(item.sigunguCode()), 20),
                    eventStartDate,
                    eventEndDate,
                    parseCoordinate(item.mapX(), new BigDecimal("-180"), new BigDecimal("180")),
                    parseCoordinate(item.mapY(), new BigDecimal("-90"), new BigDecimal("90")),
                    originImageUrl,
                    thumbnailUrl,
                    syncedAt,
                    rawData(item)
            ));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private LocalDate parseDate(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : LocalDate.parse(normalized, API_DATE_FORMAT);
    }

    private BigDecimal parseCoordinate(String value, BigDecimal minimum, BigDecimal maximum) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            BigDecimal coordinate = new BigDecimal(normalized);
            if (coordinate.compareTo(minimum) < 0 || coordinate.compareTo(maximum) > 0) {
                return null;
            }
            return coordinate.setScale(10, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Map<String, Object> rawData(SearchFestivalItem item) {
        try {
            return objectMapper.convertValue(item, RAW_DATA_TYPE);
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    private String joinAddress(String address1, String address2) {
        String first = normalize(address1);
        String second = normalize(address2);
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first + " " + second;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeImageUrl(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() > 1000) {
            return null;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            return normalized;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
