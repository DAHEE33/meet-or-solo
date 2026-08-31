package com.survey.meetorsolo.domain.tourplace.service;

import com.survey.meetorsolo.domain.tourplace.config.TourPlaceSyncProperties;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData;
import com.survey.meetorsolo.external.tourapi.client.TourApiClient;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiArrange;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TourPlaceSyncService {

    private static final Logger log = LoggerFactory.getLogger(TourPlaceSyncService.class);

    private final TourApiClient tourApiClient;
    private final TourPlaceSyncProperties properties;
    private final TourPlaceSyncMapper mapper;
    private final TourPlaceSyncWriter writer;
    private final TourPlaceSyncRetryWaiter retryWaiter;

    public TourPlaceSyncService(
            TourApiClient tourApiClient,
            TourPlaceSyncProperties properties,
            TourPlaceSyncMapper mapper,
            TourPlaceSyncWriter writer,
            TourPlaceSyncRetryWaiter retryWaiter
    ) {
        this.tourApiClient = tourApiClient;
        this.properties = properties;
        this.mapper = mapper;
        this.writer = writer;
        this.retryWaiter = retryWaiter;
    }

    public TourPlaceSyncResult synchronizeTourPlaces() {
        LocalDate syncDate = LocalDate.now(SeoulDateTime.ZONE_ID);
        boolean initialLoad = writer.countStoredTourPlaces() == 0;
        OffsetDateTime syncedAt = SeoulDateTime.now();

        int totalFetched = 0;
        int totalSynchronized = 0;
        int totalInserted = 0;
        int totalUpdated = 0;
        int totalInactive = 0;
        int totalSkipped = 0;

        for (String contentTypeId : properties.contentTypeIds()) {
            ContentTypeSyncOutcome outcome = synchronizeContentType(contentTypeId, syncDate, syncedAt);
            totalFetched += outcome.fetchedCount();
            totalSynchronized += outcome.synchronizedCount();
            totalInserted += outcome.insertedCount();
            totalUpdated += outcome.updatedCount();
            totalInactive += outcome.inactiveCount();
            totalSkipped += outcome.skippedCount();
        }

        return new TourPlaceSyncResult(
                totalFetched,
                totalSynchronized,
                totalInserted,
                totalUpdated,
                totalInactive,
                totalSkipped,
                initialLoad,
                syncedAt
        );
    }

    public long countStoredTourPlaces() {
        return writer.countStoredTourPlaces();
    }

    /**
     * 한 콘텐츠 타입의 전체 페이지를 순회하며, {@code batch-size}만큼 모일 때마다 즉시 insert/update를
     * 커밋한다. 페이지 수집 중 실패하면 이미 커밋된 배치는 그대로 남고, 아직 못 모은 나머지만 이번 회차에서
     * 반영되지 않는다. INACTIVE 정리는 이 타입의 모든 페이지가 100% 수집된 뒤에만 마지막에 한 번 실행한다.
     */
    private ContentTypeSyncOutcome synchronizeContentType(
            String contentTypeId,
            LocalDate syncDate,
            OffsetDateTime syncedAt
    ) {
        Set<String> observedContentIds = new LinkedHashSet<>();
        Set<String> mappedContentIds = new LinkedHashSet<>();
        Map<String, TourPlaceSyncData> buffer = new LinkedHashMap<>();

        int totalFetched = 0;
        int totalSkipped = 0;
        int totalSynchronized = 0;
        int totalInserted = 0;
        int totalUpdated = 0;

        TourApiPage<SearchTourPlaceItem> firstPage = fetchPage(1, contentTypeId);
        validatePage(firstPage, 1);

        int responsePageSize = firstPage.numOfRows() > 0
                ? firstPage.numOfRows()
                : properties.pageSize();
        int totalPages = calculateTotalPages(firstPage.totalCount(), responsePageSize);
        if (totalPages > properties.maxPages()) {
            throw malformed("응답 페이지 수가 설정된 최대값을 초과했습니다. contentTypeId=" + contentTypeId);
        }
        if (firstPage.totalCount() == 0 && !firstPage.items().isEmpty()) {
            throw malformed("전체 건수는 0이지만 응답 항목이 존재합니다. contentTypeId=" + contentTypeId);
        }
        if (totalPages > 0 && firstPage.items().isEmpty()) {
            throw malformed("전체 건수는 존재하지만 첫 페이지가 비어 있습니다. contentTypeId=" + contentTypeId);
        }

        totalFetched += firstPage.items().size();
        totalSkipped += accumulate(firstPage.items(), contentTypeId, syncedAt, observedContentIds, mappedContentIds, buffer);
        int receivedCount = firstPage.items().size();

        BatchFlushResult flush = flushIfFull(buffer);
        totalSynchronized += flush.count();
        totalInserted += flush.insertedCount();
        totalUpdated += flush.updatedCount();

        for (int pageNo = 2; pageNo <= totalPages; pageNo++) {
            TourApiPage<SearchTourPlaceItem> page = fetchPage(pageNo, contentTypeId);
            validatePage(page, pageNo);
            if (page.items().isEmpty()) {
                throw malformed("마지막 페이지 전에 빈 페이지가 반환됐습니다. contentTypeId=" + contentTypeId);
            }
            totalFetched += page.items().size();
            totalSkipped += accumulate(page.items(), contentTypeId, syncedAt, observedContentIds, mappedContentIds, buffer);
            receivedCount += page.items().size();

            flush = flushIfFull(buffer);
            totalSynchronized += flush.count();
            totalInserted += flush.insertedCount();
            totalUpdated += flush.updatedCount();
        }

        if (totalFetched > 0 && mappedContentIds.isEmpty()) {
            throw malformed("응답 항목을 하나도 DB 동기화 데이터로 변환할 수 없습니다. contentTypeId=" + contentTypeId);
        }
        if (receivedCount < firstPage.totalCount()) {
            throw malformed("전체 건수보다 적은 응답 항목이 반환됐습니다. contentTypeId=" + contentTypeId);
        }
        if (totalSkipped > 0) {
            log.warn("Tour place sync skipped invalid or duplicate items. contentTypeId={}, count={}",
                    contentTypeId, totalSkipped);
        }

        if (!buffer.isEmpty()) {
            List<TourPlaceSyncData> remainder = List.copyOf(buffer.values());
            TourPlaceSyncWriteResult result = writer.upsertBatch(remainder);
            totalSynchronized += remainder.size();
            totalInserted += result.insertedCount();
            totalUpdated += result.updatedCount();
            buffer.clear();
        }

        TourPlaceSyncScope syncScope = new TourPlaceSyncScope(syncDate, contentTypeId, observedContentIds);
        int inactiveCount = writer.markMissingInactive(syncScope);

        return new ContentTypeSyncOutcome(
                totalFetched,
                totalSynchronized,
                totalInserted,
                totalUpdated,
                inactiveCount,
                totalSkipped
        );
    }

    private int accumulate(
            List<SearchTourPlaceItem> items,
            String contentTypeId,
            OffsetDateTime syncedAt,
            Set<String> observedContentIds,
            Set<String> mappedContentIds,
            Map<String, TourPlaceSyncData> buffer
    ) {
        int skipped = 0;
        for (SearchTourPlaceItem item : items) {
            if (item != null && item.contentId() != null && !item.contentId().isBlank()) {
                observedContentIds.add(item.contentId().trim());
            }
            Optional<TourPlaceSyncData> mapped = mapper.toSyncData(item, contentTypeId, syncedAt);
            if (mapped.isEmpty()) {
                skipped++;
                continue;
            }
            TourPlaceSyncData data = mapped.get();
            if (!mappedContentIds.add(data.contentId())) {
                skipped++;
            }
            buffer.put(data.contentId(), data);
        }
        return skipped;
    }

    private BatchFlushResult flushIfFull(Map<String, TourPlaceSyncData> buffer) {
        if (buffer.size() < properties.batchSize()) {
            return BatchFlushResult.EMPTY;
        }
        List<TourPlaceSyncData> chunk = List.copyOf(buffer.values());
        buffer.clear();
        TourPlaceSyncWriteResult result = writer.upsertBatch(chunk);
        return new BatchFlushResult(chunk.size(), result.insertedCount(), result.updatedCount());
    }

    private record ContentTypeSyncOutcome(
            int fetchedCount,
            int synchronizedCount,
            int insertedCount,
            int updatedCount,
            int inactiveCount,
            int skippedCount
    ) {
    }

    private record BatchFlushResult(int count, int insertedCount, int updatedCount) {
        private static final BatchFlushResult EMPTY = new BatchFlushResult(0, 0, 0);
    }

    private TourApiPage<SearchTourPlaceItem> fetchPage(int pageNo, String contentTypeId) {
        SearchTourPlaceRequest request = new SearchTourPlaceRequest(
                contentTypeId,
                pageNo,
                properties.pageSize(),
                TourApiArrange.MODIFIED,
                properties.regionCode(),
                null
        );

        for (int attempt = 1; attempt <= properties.retryMaxAttempts(); attempt++) {
            try {
                return tourApiClient.searchTourPlaces(request);
            } catch (TourApiClientException exception) {
                if (!isRetryable(exception) || attempt == properties.retryMaxAttempts()) {
                    throw exception;
                }
                Duration delay = retryDelay(attempt);
                log.warn(
                        "Tour place API page request will be retried. contentTypeId={}, pageNo={}, attempt={}/{}, type={}, httpStatus={}, nextDelayMs={}",
                        contentTypeId,
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
        throw new IllegalStateException("관광지 API 재시도 흐름이 정상적으로 종료되지 않았습니다.");
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

    private void validatePage(TourApiPage<SearchTourPlaceItem> page, int requestedPageNo) {
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
