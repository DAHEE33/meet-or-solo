package com.survey.meetorsolo.domain.matching.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.service.PoolEntryMatchingOrchestrationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MatchingPoolEnteredEventHandlerTest {

    private final PoolEntryMatchingOrchestrationService orchestrationService =
            Mockito.mock(PoolEntryMatchingOrchestrationService.class);
    private final MatchingPoolEnteredEventHandler handler =
            new MatchingPoolEnteredEventHandler(orchestrationService);

    @Test
    void event의_pool_회원_축제로_pool_entry_orchestration을_호출한다() {
        MatchingPoolEnteredEvent event = new MatchingPoolEnteredEvent(10L, 20L, 30L);

        handler.handle(event);

        verify(orchestrationService).run(10L, 20L, 30L);
    }

    @Test
    void trigger_실패를_listener_밖으로_전파하지_않는다() {
        MatchingPoolEnteredEvent event = new MatchingPoolEnteredEvent(10L, 20L, 30L);
        when(orchestrationService.run(10L, 20L, 30L))
                .thenThrow(new IllegalStateException("trigger failed"));

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
    }
}
