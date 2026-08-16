package com.survey.meetorsolo.domain.matching.scheduler;

import static org.mockito.Mockito.*;
import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.service.MatchProposalTimeoutService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MatchProposalTimeoutSchedulerTest {
    @Test void 기존_batch_size로_timeout_service만_호출한다() {
        MatchProposalTimeoutService service=mock(MatchProposalTimeoutService.class);
        MatchingSchedulerProperties properties=new MatchingSchedulerProperties(true,Duration.ofSeconds(5),
                Duration.ofSeconds(30),Duration.ofSeconds(30),7);
        new MatchProposalTimeoutScheduler(service,properties).run();
        verify(service).runBatch(7); verifyNoMoreInteractions(service);
    }
}
