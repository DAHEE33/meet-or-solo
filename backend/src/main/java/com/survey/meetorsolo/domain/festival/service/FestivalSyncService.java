package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.festival.config.FestivalSyncProperties;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.external.tourapi.client.TourApiClient;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiArrange;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FestivalSyncService {

    private static final Logger log = LoggerFactory.getLogger(FestivalSyncService.class);

    private final TourApiClient tourApiClient;
    private final FestivalSyncProperties properties;
    private final FestivalSyncMapper mapper;
    private final FestivalSyncWriter writer;
    private final FestivalSyncRetryWaiter retryWaiter;

    public FestivalSyncService(
            TourApiClient tourApiClient,
            FestivalSyncProperties properties,
            FestivalSyncMapper mapper,
            FestivalSyncWriter writer,
            FestivalSyncRetryWaiter retryWaiter
    ) {
        this.tourApiClient = tourApiClient;
        this.properties = properties;
        this.mapper = mapper;
        this.writer = writer;
        this.retryWaiter = retryWaiter;
    }

    public FestivalSyncResult synchronizeFestivals() {
        LocalDate syncDate = LocalDate.now(SeoulDateTime.ZONE_ID);
        LocalDate eventStartDate = syncDate.minusDays(properties.lookbackDays());
        LocalDate eventEndDate = syncDate.plusDays(properties.lookaheadDays());

        List<SearchFestivalItem> fetchedItems = fetchAllPages(eventStartDate, eventEndDate);
        OffsetDateTime syncedAt = SeoulDateTime.now();
        Map<String, FestivalSyncData> uniqueSyncData = new LinkedHashMap<>();
        for (SearchFestivalItem item : fetchedItems) {
            mapper.toSyncData(item, syncedAt)
                    .ifPresent(data -> uniqueSyncData.put(data.contentId(), data));
        }

        if (!fetchedItems.isEmpty() && uniqueSyncData.isEmpty()) {
            throw malformed("응답 항목을 하나도 DB 동기화 데이터로 변환할 수 없습니다.");
        }

        int skippedCount = fetchedItems.size() - uniqueSyncData.size();
        if (skippedCount > 0) {
            log.warn("Festival sync skipped invalid or duplicate items. count={}", skippedCount);
        }

        FestivalSyncWriteResult writeResult = writer.upsert(uniqueSyncData.values(), syncDate);
        return new FestivalSyncResult(
                fetchedItems.size(),
                uniqueSyncData.size(),
                writeResult.insertedCount(),
                writeResult.updatedCount(),
                skippedCount,
                writeResult.initialLoad(),
                syncedAt
        );
    }

    public long countStoredFestivals() {
        return writer.countStoredFestivals();
    }

    private List<SearchFestivalItem> fetchAllPages(
            LocalDate eventStartDate,
            LocalDate eventEndDate
    ) {
        List<SearchFestivalItem> items = new ArrayList<>();
        TourApiPage<SearchFestivalItem> firstPage = fetchPage(
                1,
                eventStartDate,
                eventEndDate
        );
        validatePage(firstPage, 1);
        items.addAll(firstPage.items());

        int responsePageSize = firstPage.numOfRows() > 0
                ? firstPage.numOfRows()
                : properties.pageSize();
        int totalPages = calculateTotalPages(firstPage.totalCount(), responsePageSize);
        if (totalPages > properties.maxPages()) {
            throw malformed("응답 페이지 수가 설정된 최대값을 초과했습니다.");
        }
        if (firstPage.totalCount() == 0 && !firstPage.items().isEmpty()) {
            throw malformed("전체 건수는 0이지만 응답 항목이 존재합니다.");
        }
        if (totalPages > 0 && firstPage.items().isEmpty()) {
            throw malformed("전체 건수는 존재하지만 첫 페이지가 비어 있습니다.");
        }

        for (int pageNo = 2; pageNo <= totalPages; pageNo++) {
            TourApiPage<SearchFestivalItem> page = fetchPage(
                    pageNo,
                    eventStartDate,
                    eventEndDate
            );
            validatePage(page, pageNo);
            if (page.items().isEmpty()) {
                throw malformed("마지막 페이지 전에 빈 페이지가 반환됐습니다.");
            }
            items.addAll(page.items());
        }
        if (items.size() < firstPage.totalCount()) {
            throw malformed("전체 건수보다 적은 응답 항목이 반환됐습니다.");
        }
        return items;
    }

    private TourApiPage<SearchFestivalItem> fetchPage(
            int pageNo,
            LocalDate eventStartDate,
            LocalDate eventEndDate
    ) {
        SearchFestivalRequest request = new SearchFestivalRequest(
                eventStartDate,
                eventEndDate,
                null,
                pageNo,
                properties.pageSize(),
                TourApiArrange.MODIFIED,
                properties.regionCode(),
                null,
                properties.classificationSystem1(),
                properties.classificationSystem2(),
                null
        );

        for (int attempt = 1; attempt <= properties.retryMaxAttempts(); attempt++) {
            try {
                return tourApiClient.searchFestivals(request);
            } catch (TourApiClientException exception) {
                if (!isRetryable(exception) || attempt == properties.retryMaxAttempts()) {
                    throw exception;
                }
                Duration delay = retryDelay(attempt);
                log.warn(
                        "Festival API page request will be retried. pageNo={}, attempt={}/{}, type={}, httpStatus={}, nextDelayMs={}",
                        pageNo,
                        attempt,
                        properties.retryMaxAttempts(),
                        exception.getErrorType(),
                        exception.getHttpStatus(),
                        delay.toMillis()
                );
                retryWaiter.waitFor(delay);
            }
        }
        throw new IllegalStateException("축제 API 재시도 흐름이 정상적으로 종료되지 않았습니다.");
    }

    private boolean isRetryable(TourApiClientException exception) {
        if (exception.getErrorType() == TourApiErrorType.NETWORK
                || exception.getErrorType() == TourApiErrorType.RATE_LIMIT) {
            return true;
        }
        Integer status = exception.getHttpStatus();
        return exception.getErrorType() == TourApiErrorType.HTTP
                && status != null
                && status >= 500
                && status <= 599;
    }

    private Duration retryDelay(int failedAttempt) {
        Duration delay = properties.retryInitialDelay();
        for (int exponent = 1; exponent < failedAttempt; exponent++) {
            if (delay.compareTo(properties.retryMaxDelay()) >= 0) {
                return properties.retryMaxDelay();
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(properties.retryMaxDelay()) > 0
                ? properties.retryMaxDelay()
                : delay;
    }

    private void validatePage(TourApiPage<SearchFestivalItem> page, int requestedPageNo) {
        if (page.pageNo() != requestedPageNo) {
            throw malformed("요청한 페이지 번호와 응답 페이지 번호가 다릅니다.");
        }
        if (page.totalCount() < 0) {
            throw malformed("전체 결과 수가 음수입니다.");
        }
    }

    private int calculateTotalPages(int totalCount, int pageSize) {
        if (totalCount == 0) {
            return 0;
        }
        return (int) (((long) totalCount + pageSize - 1) / pageSize);
    }

    private TourApiClientException malformed(String detail) {
        return TourApiClientException.withDetail(
                TourApiErrorType.MALFORMED_RESPONSE,
                detail
        );
    }
}
