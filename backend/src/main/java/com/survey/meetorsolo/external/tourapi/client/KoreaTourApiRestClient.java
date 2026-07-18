package com.survey.meetorsolo.external.tourapi.client;

import com.survey.meetorsolo.external.tourapi.config.TourApiProperties;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import com.survey.meetorsolo.external.tourapi.support.TourApiResponseParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
public class KoreaTourApiRestClient implements TourApiClient {

    private static final Logger log = LoggerFactory.getLogger(KoreaTourApiRestClient.class);
    private static final String SEARCH_FESTIVAL_OPERATION = "searchFestival2";
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern PERCENT_ENCODED = Pattern.compile("%[0-9A-Fa-f]{2}");

    private final RestClient restClient;
    private final TourApiProperties properties;
    private final TourApiResponseParser responseParser;

    public KoreaTourApiRestClient(
            @Qualifier("tourApiRestClient") RestClient restClient,
            TourApiProperties properties,
            TourApiResponseParser responseParser
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.responseParser = responseParser;
    }

    @Override
    public TourApiPage<SearchFestivalItem> searchFestivals(SearchFestivalRequest request) {
        URI uri = buildSearchFestivalUri(request);
        long startedAt = System.nanoTime();
        String responseBody;

        try {
            responseBody = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            TourApiErrorType errorType = status == 429
                    ? TourApiErrorType.RATE_LIMIT
                    : TourApiErrorType.HTTP;
            log.warn("Tour API request failed. operation={}, status={}",
                    SEARCH_FESTIVAL_OPERATION, status);
            throw TourApiClientException.forHttpError(errorType, status, exception);
        } catch (RestClientException exception) {
            log.warn("Tour API request failed. operation={}, cause={}",
                    SEARCH_FESTIVAL_OPERATION, exception.getClass().getSimpleName());
            throw new TourApiClientException(TourApiErrorType.NETWORK, exception);
        }

        try {
            TourApiPage<SearchFestivalItem> page = responseParser.parsePage(
                    responseBody,
                    SearchFestivalItem.class
            );
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.debug("Tour API request succeeded. operation={}, pageNo={}, itemCount={}, elapsedMs={}",
                    SEARCH_FESTIVAL_OPERATION, page.pageNo(), page.items().size(), elapsedMs);
            return page;
        } catch (TourApiClientException exception) {
            log.warn("Tour API response failed. operation={}, type={}, remoteCode={}",
                    SEARCH_FESTIVAL_OPERATION,
                    exception.getErrorType(),
                    exception.getRemoteCode());
            throw exception;
        }
    }

    private URI buildSearchFestivalUri(SearchFestivalRequest request) {
        String baseUrl = required(properties.baseUrl(), "TOUR_API_BASE_URL");
        String serviceKey = encodedServiceKey(properties.serviceKey());
        String mobileOs = required(properties.mobileOs(), "TOUR_API_MOBILE_OS");
        String mobileApp = required(properties.mobileApp(), "TOUR_API_MOBILE_APP");

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(SEARCH_FESTIVAL_OPERATION)
                .queryParam("serviceKey", serviceKey)
                .queryParam("numOfRows", request.numOfRows())
                .queryParam("pageNo", request.pageNo())
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("_type", "json")
                .queryParam("arrange", request.arrange().getCode())
                .queryParam("eventStartDate", API_DATE_FORMAT.format(request.eventStartDate()));

        addDate(builder, "eventEndDate", request.eventEndDate());
        addDate(builder, "modifiedtime", request.modifiedTime());
        addText(builder, "lDongRegnCd", request.regionCode());
        addText(builder, "lDongSignguCd", request.sigunguCode());
        addText(builder, "lclsSystm1", request.classificationSystem1());
        addText(builder, "lclsSystm2", request.classificationSystem2());
        addText(builder, "lclsSystm3", request.classificationSystem3());

        return builder.build(true).toUri();
    }

    private String encodedServiceKey(String value) {
        String serviceKey = required(value, "TOUR_API_KEY 또는 TOURISM-API-KEY");
        if (PERCENT_ENCODED.matcher(serviceKey).find()) {
            serviceKey = UriUtils.decode(serviceKey, StandardCharsets.UTF_8);
        }
        return UriUtils.encodeQueryParam(serviceKey, StandardCharsets.UTF_8)
                .replace("+", "%2B");
    }

    private void addDate(UriComponentsBuilder builder, String name, java.time.LocalDate value) {
        if (value != null) {
            builder.queryParam(name, API_DATE_FORMAT.format(value));
        }
    }

    private void addText(UriComponentsBuilder builder, String name, String value) {
        if (value != null) {
            builder.queryParam(name, value);
        }
    }

    private String required(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw TourApiClientException.withDetail(
                    TourApiErrorType.CONFIGURATION,
                    environmentVariable + " 설정이 필요합니다."
            );
        }
        return value.trim();
    }
}
