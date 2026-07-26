package com.survey.meetorsolo.domain.tourplace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TourPlaceSyncMapper {

    private static final TypeReference<LinkedHashMap<String, Object>> RAW_DATA_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public TourPlaceSyncMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<TourPlaceSyncData> toSyncData(
            SearchTourPlaceItem item,
            String expectedContentTypeId,
            OffsetDateTime syncedAt
    ) {
        if (item == null) {
            return Optional.empty();
        }

        String contentId = normalize(item.contentId());
        String contentTypeId = normalize(item.contentTypeId());
        String title = normalize(item.title());
        if (contentId == null || contentId.length() > 50
                || !expectedContentTypeId.equals(contentTypeId)
                || title == null) {
            return Optional.empty();
        }

        String imageUrl = normalizeImageUrl(item.firstImage());
        if (imageUrl == null) {
            imageUrl = normalizeImageUrl(item.firstImageThumbnail());
        }

        return Optional.of(new TourPlaceSyncData(
                contentId,
                contentTypeId,
                truncate(title, 255),
                truncate(joinAddress(item.address1(), item.address2()), 500),
                parseCoordinate(item.mapX(), new BigDecimal("-180"), new BigDecimal("180")),
                parseCoordinate(item.mapY(), new BigDecimal("-90"), new BigDecimal("90")),
                truncate(normalize(item.telephone()), 100),
                imageUrl,
                syncedAt,
                rawData(item)
        ));
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

    private Map<String, Object> rawData(SearchTourPlaceItem item) {
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
