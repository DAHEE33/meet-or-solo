package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchGroupComposer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MatchingOrchestrationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T06:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
    private final MatchPoolCleanupService cleanup = mock(MatchPoolCleanupService.class);
    private final SchedulerMatchPoolClaimService claim = mock(SchedulerMatchPoolClaimService.class);
    private final MatchingBatchReader reader = mock(MatchingBatchReader.class);
    private final MatchGroupComposer composer = mock(MatchGroupComposer.class);
    private final MatchProposalCreationService creation = mock(MatchProposalCreationService.class);
    private final MatchPoolReleaseService release = mock(MatchPoolReleaseService.class);
    private MatchingOrchestrationService service;

    @BeforeEach void setUp() {
        MatchingSchedulerProperties properties = new MatchingSchedulerProperties(false, Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofSeconds(30), 20);
        service = new MatchingOrchestrationService(CLOCK, () -> "fixed-token", properties, cleanup, claim,
                reader, composer, creation, release);
        when(release.release("fixed-token", NOW)).thenReturn(new MatchPoolReleaseResult(0));
    }

    @Test void 후보가_없어도_cleanup_claim_release_순서로_안전하게_종료한다() {
        when(claim.claim(NOW, 20, "fixed-token")).thenReturn(new MatchPoolClaimResult("fixed-token", List.of()));
        MatchingOrchestrationResult result = service.runTick();
        assertThat(result.claimedCount()).isZero();
        InOrder order = inOrder(cleanup, claim, release);
        order.verify(cleanup).cleanup(NOW, NOW.minusSeconds(30));
        order.verify(claim).claim(NOW, 20, "fixed-token");
        order.verify(release).release("fixed-token", NOW);
    }

    @Test void orchestration_예외에도_release하고_release_실패는_원래_예외에_suppressed한다() {
        IllegalStateException original = new IllegalStateException("claim failed");
        IllegalStateException releaseFailure = new IllegalStateException("release failed");
        when(claim.claim(NOW, 20, "fixed-token")).thenThrow(original);
        when(release.release("fixed-token", NOW)).thenThrow(releaseFailure);
        assertThatThrownBy(service::runTick).isSameAs(original)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(releaseFailure));
        verify(release).release("fixed-token", NOW);
    }

    @Test void 한_group_실패는_다른_group_생성을_막지_않고_마지막에_release한다() {
        MatchGroupCombination first = mock(MatchGroupCombination.class);
        MatchGroupCombination second = mock(MatchGroupCombination.class);
        MatchingBatchReader.MatchingBatch batch = new MatchingBatchReader.MatchingBatch(List.of(), java.util.Set.of());
        when(claim.claim(NOW, 20, "fixed-token")).thenReturn(new MatchPoolClaimResult("fixed-token", List.of(1L)));
        when(reader.read("fixed-token")).thenReturn(batch);
        when(composer.compose(org.mockito.ArgumentMatchers.eq(batch.candidates()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(first, second));
        when(first.candidates()).thenReturn(List.of());
        when(creation.createInitial(first, "fixed-token", NOW, Duration.ofSeconds(30)))
                .thenThrow(new MatchProposalCreationException("failed"));
        when(creation.createInitial(second, "fixed-token", NOW, Duration.ofSeconds(30)))
                .thenReturn(new MatchProposalCreationResult(10L, List.of(1L)));
        MatchingOrchestrationResult result = service.runTick();
        assertThat(result.createdAttemptIds()).containsExactly(10L);
        assertThat(result.failedGroupCount()).isOne();
        verify(release).release("fixed-token", NOW);
    }
}
