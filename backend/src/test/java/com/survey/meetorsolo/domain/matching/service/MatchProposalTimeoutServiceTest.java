package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.survey.meetorsolo.domain.matching.repository.MatchAttemptRepository;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchProposalTimeoutServiceTest {
    private static final OffsetDateTime NOW=OffsetDateTime.of(2026,7,21,12,0,0,0,ZoneOffset.ofHours(9));
    @Test void batch상한을_조회에_전달하고_attempt별_실패를_격리한다() {
        MatchAttemptRepository attempts=mock(MatchAttemptRepository.class);
        MatchProposalResponseService responses=mock(MatchProposalResponseService.class);
        when(attempts.findTimeoutCandidateIds(NOW,2)).thenReturn(List.of(1L,2L));
        when(responses.timeoutAttempt(1L,NOW)).thenReturn(new MatchProposalResponseResult(1,11,"TIMEOUT","FAILED"));
        when(responses.timeoutAttempt(2L,NOW)).thenThrow(new IllegalStateException("forced"));
        var service=new MatchProposalTimeoutService(Clock.fixed(NOW.toInstant(),ZoneId.of("Asia/Seoul")),attempts,responses);
        assertThat(service.runBatch(2)).isEqualTo(new MatchProposalTimeoutResult(2,1,1));
        verify(attempts).findTimeoutCandidateIds(NOW,2); verify(responses).timeoutAttempt(1L,NOW); verify(responses).timeoutAttempt(2L,NOW);
    }
}
