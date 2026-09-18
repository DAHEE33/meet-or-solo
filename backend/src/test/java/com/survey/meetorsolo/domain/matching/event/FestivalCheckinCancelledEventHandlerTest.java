package com.survey.meetorsolo.domain.matching.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.event.FestivalCheckinCancelledEvent;
import com.survey.meetorsolo.domain.matching.service.MatchPoolCheckinCancellationService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FestivalCheckinCancelledEventHandlerTest {

    private static final OffsetDateTime CANCELLED_AT = OffsetDateTime.parse("2026-07-24T15:00:00+09:00");

    private final MatchPoolCheckinCancellationService cancellationService =
            Mockito.mock(MatchPoolCheckinCancellationService.class);
    private final FestivalCheckinCancelledEventHandler handler =
            new FestivalCheckinCancelledEventHandler(cancellationService);

    @Test
    void event의_회원_축제로_WAITING_pool_정리를_호출한다() {
        FestivalCheckinCancelledEvent event = new FestivalCheckinCancelledEvent(10L, 20L, CANCELLED_AT);

        handler.handle(event);

        verify(cancellationService).cancelWaitingPool(10L, 20L, CANCELLED_AT);
    }

    @Test
    void 정리_실패를_listener_밖으로_전파하지_않는다() {
        FestivalCheckinCancelledEvent event = new FestivalCheckinCancelledEvent(10L, 20L, CANCELLED_AT);
        when(cancellationService.cancelWaitingPool(10L, 20L, CANCELLED_AT))
                .thenThrow(new IllegalStateException("cleanup failed"));

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
    }
}
