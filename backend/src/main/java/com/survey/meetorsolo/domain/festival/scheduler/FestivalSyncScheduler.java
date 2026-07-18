package com.survey.meetorsolo.domain.festival.scheduler;

import com.survey.meetorsolo.domain.festival.service.FestivalSyncResult;
import com.survey.meetorsolo.domain.festival.service.FestivalSyncService;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.festival.sync",
        name = "enabled",
        havingValue = "true"
)
public class FestivalSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(FestivalSyncScheduler.class);

    private final FestivalSyncService festivalSyncService;

    public FestivalSyncScheduler(FestivalSyncService festivalSyncService) {
        this.festivalSyncService = festivalSyncService;
    }

    @Scheduled(
            initialDelayString = "${app.festival.sync.initial-delay}",
            fixedDelayString = "${app.festival.sync.fixed-delay}"
    )
    public void synchronizeFestivals() {
        try {
            FestivalSyncResult result = festivalSyncService.synchronizeFestivals();
            log.info(
                    "Festival sync succeeded. initialLoad={}, fetched={}, synchronized={}, inserted={}, updated={}, images={}, ended={}, inactive={}, skipped={}",
                    result.initialLoad(),
                    result.fetchedCount(),
                    result.synchronizedCount(),
                    result.insertedCount(),
                    result.updatedCount(),
                    result.synchronizedImageCount(),
                    result.endedCount(),
                    result.inactiveCount(),
                    result.skippedCount()
            );
        } catch (TourApiClientException exception) {
            long storedCount = safeStoredCount();
            log.warn(
                    "Festival sync failed. fallbackState={}, storedCount={}, type={}, httpStatus={}, remoteCode={}",
                    fallbackState(storedCount),
                    storedCount,
                    exception.getErrorType(),
                    exception.getHttpStatus(),
                    exception.getRemoteCode()
            );
        } catch (RuntimeException exception) {
            long storedCount = safeStoredCount();
            log.error(
                    "Festival sync failed unexpectedly. fallbackState={}, storedCount={}, cause={}",
                    fallbackState(storedCount),
                    storedCount,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private String fallbackState(long storedCount) {
        if (storedCount < 0) {
            return "UNKNOWN";
        }
        return storedCount == 0 ? "NO_DATA" : "STALE_DATA";
    }

    private long safeStoredCount() {
        try {
            return festivalSyncService.countStoredFestivals();
        } catch (RuntimeException exception) {
            return -1;
        }
    }
}
