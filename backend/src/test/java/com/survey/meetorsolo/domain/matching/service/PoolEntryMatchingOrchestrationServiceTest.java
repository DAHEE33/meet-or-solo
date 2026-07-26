package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.entity.MatchAttempt;
import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchGroupComposer;
import com.survey.meetorsolo.domain.matching.group.MatchingCandidate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PoolEntryMatchingOrchestrationServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-26T06:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
    private final PoolEntryMatchPoolClaimService claimService = mock(PoolEntryMatchPoolClaimService.class);
    private final MatchingBatchReader batchReader = mock(MatchingBatchReader.class);
    private final MatchGroupComposer groupComposer = mock(MatchGroupComposer.class);
    private final MatchProposalCreationService creationService = mock(MatchProposalCreationService.class);
    private final MatchPoolReleaseService releaseService = mock(MatchPoolReleaseService.class);
    private PoolEntryMatchingOrchestrationService service;

    @BeforeEach
    void setUp() {
        MatchingSchedulerProperties properties = new MatchingSchedulerProperties(
                false,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                20
        );
        service = new PoolEntryMatchingOrchestrationService(
                CLOCK,
                () -> "pool-entry-token",
                properties,
                claimService,
                batchReader,
                groupComposer,
                creationService,
                releaseService
        );
        when(releaseService.release("pool-entry-token", NOW)).thenReturn(new MatchPoolReleaseResult(0));
    }

    @Test
    void requester가_포함된_group만_POOL_ENTRY로_생성한다() {
        MatchingCandidate requester = candidate(1L, 11L);
        MatchingCandidate other = candidate(2L, 12L);
        MatchGroupCombination requesterGroup = new MatchGroupCombination(
                List.of(requester, other),
                java.math.BigDecimal.ZERO
        );
        when(claimService.claim(1L, 11L, 100L, NOW, 20, "pool-entry-token"))
                .thenReturn(new MatchPoolClaimResult("pool-entry-token", List.of(1L, 2L)));
        when(batchReader.read("pool-entry-token")).thenReturn(
                new MatchingBatchReader.MatchingBatch(List.of(requester, other), Set.of())
        );
        when(groupComposer.compose(any(), any())).thenReturn(List.of(requesterGroup));
        when(creationService.createInitial(
                requesterGroup,
                "pool-entry-token",
                NOW,
                Duration.ofSeconds(30),
                MatchAttempt.CREATED_BY_POOL_ENTRY
        )).thenReturn(new MatchProposalCreationResult(50L, List.of(1L, 2L)));

        MatchingOrchestrationResult result = service.run(1L, 11L, 100L);

        assertThat(result.createdAttemptIds()).containsExactly(50L);
        verify(creationService).createInitial(
                requesterGroup,
                "pool-entry-token",
                NOW,
                Duration.ofSeconds(30),
                MatchAttempt.CREATED_BY_POOL_ENTRY
        );
        verify(releaseService).release("pool-entry-token", NOW);
    }

    @Test
    void requester_group이_없으면_proposal을_만들지_않고_lock을_release한다() {
        MatchingCandidate requester = candidate(1L, 11L);
        when(claimService.claim(1L, 11L, 100L, NOW, 20, "pool-entry-token"))
                .thenReturn(new MatchPoolClaimResult("pool-entry-token", List.of(1L)));
        when(batchReader.read("pool-entry-token")).thenReturn(
                new MatchingBatchReader.MatchingBatch(List.of(requester), Set.of())
        );
        when(groupComposer.compose(any(), any())).thenReturn(List.of());
        when(releaseService.release("pool-entry-token", NOW)).thenReturn(new MatchPoolReleaseResult(1));

        MatchingOrchestrationResult result = service.run(1L, 11L, 100L);

        assertThat(result.createdAttemptIds()).isEmpty();
        assertThat(result.releasedCount()).isOne();
        verify(releaseService).release("pool-entry-token", NOW);
    }

    private MatchingCandidate candidate(long poolId, long memberId) {
        return new MatchingCandidate(
                poolId,
                memberId,
                100L,
                2,
                false,
                NOW.minusSeconds(poolId),
                List.of()
        );
    }
}
