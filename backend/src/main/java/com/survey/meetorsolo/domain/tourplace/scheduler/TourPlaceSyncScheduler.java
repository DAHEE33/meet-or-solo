package com.survey.meetorsolo.domain.tourplace.scheduler;

import com.survey.meetorsolo.domain.tourplace.service.TourPlaceSyncResult;
import com.survey.meetorsolo.domain.tourplace.service.TourPlaceSyncService;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.tour-place.sync",
        name = "enabled",
        havingValue = "true"
)
public class TourPlaceSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(TourPlaceSyncScheduler.class);

    private final TourPlaceSyncService tourPlaceSyncService;

    public TourPlaceSyncScheduler(TourPlaceSyncService tourPlaceSyncService) {
        this.tourPlaceSyncService = tourPlaceSyncService;
    }

    @Scheduled(
            initialDelayString = "${app.tour-place.sync.initial-delay}",
            fixedDelayString = "${app.tour-place.sync.fixed-delay}"
    )
    public void synchronizeTourPlaces() {
        try {
            TourPlaceSyncResult result = tourPlaceSyncService.synchronizeTourPlaces();
            log.info(
                    "Tour place sync succeeded. initialLoad={}, fetched={}, synchronized={}, inserted={}, updated={}, inactive={}, skipped={}",
                    result.initialLoad(),
                    result.fetchedCount(),
                    result.synchronizedCount(),
                    result.insertedCount(),
                    result.updatedCount(),
                    result.inactiveCount(),
                    result.skippedCount()
            );
        } catch (TourApiClientException exception) {
            long storedCount = safeStoredCount();
            log.warn(
                    "Tour place sync failed. fallbackState={}, storedCount={}, type={}, httpStatus={}, remoteCode={}",
                    fallbackState(storedCount),
                    storedCount,
                    exception.getErrorType(),
                    exception.getHttpStatus(),
                    exception.getRemoteCode()
            );
        } catch (RuntimeException exception) {
            long storedCount = safeStoredCount();
            log.error(
                    "Tour place sync failed unexpectedly. fallbackState={}, storedCount={}, cause={}",
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
            return tourPlaceSyncService.countStoredTourPlaces();
        } catch (RuntimeException exception) {
            return -1;
        }
    }
}
