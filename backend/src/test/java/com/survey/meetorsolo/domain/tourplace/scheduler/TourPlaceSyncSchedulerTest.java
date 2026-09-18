package com.survey.meetorsolo.domain.tourplace.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.tourplace.service.TourPlaceSyncResult;
import com.survey.meetorsolo.domain.tourplace.service.TourPlaceSyncService;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourPlaceSyncSchedulerTest {

    @Mock
    private TourPlaceSyncService tourPlaceSyncService;

    @Test
    void 스케줄_실행이_동기화_서비스를_호출한다() {
        when(tourPlaceSyncService.synchronizeTourPlaces()).thenReturn(new TourPlaceSyncResult(
                3,
                3,
                3,
                0,
                0,
                0,
                true,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00")
        ));
        TourPlaceSyncScheduler scheduler = new TourPlaceSyncScheduler(tourPlaceSyncService);

        scheduler.synchronizeTourPlaces();

        verify(tourPlaceSyncService).synchronizeTourPlaces();
    }

    @Test
    void 최초_동기화가_실패하고_DB가_비어_있어도_예외를_전파하지_않는다() {
        when(tourPlaceSyncService.synchronizeTourPlaces())
                .thenThrow(new TourApiClientException(TourApiErrorType.NETWORK));
        when(tourPlaceSyncService.countStoredTourPlaces()).thenReturn(0L);
        TourPlaceSyncScheduler scheduler = new TourPlaceSyncScheduler(tourPlaceSyncService);

        assertThatCode(scheduler::synchronizeTourPlaces).doesNotThrowAnyException();

        verify(tourPlaceSyncService).countStoredTourPlaces();
    }

    @Test
    void 재동기화가_실패하면_기존_DB_데이터를_유지하고_예외를_전파하지_않는다() {
        when(tourPlaceSyncService.synchronizeTourPlaces())
                .thenThrow(new TourApiClientException(TourApiErrorType.RATE_LIMIT));
        when(tourPlaceSyncService.countStoredTourPlaces()).thenReturn(5L);
        TourPlaceSyncScheduler scheduler = new TourPlaceSyncScheduler(tourPlaceSyncService);

        assertThatCode(scheduler::synchronizeTourPlaces).doesNotThrowAnyException();

        verify(tourPlaceSyncService).countStoredTourPlaces();
    }
}
