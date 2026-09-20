package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchGroupComposer;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MatchingOrchestrationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T06:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
    private static final String BASE_TOKEN = "fixed-token";
    private static final long WINDOW_ID = 77L;
    private static final long FESTIVAL_ID = 9L;
    /** 구간마다 다른 token을 쓴다. 같은 token을 공유하면 서로 다른 구간의 후보가 한 배치로 섞인다. */
    private static final String WINDOW_TOKEN = BASE_TOKEN + ":" + WINDOW_ID;

    private final MatchPoolCleanupService cleanup = mock(MatchPoolCleanupService.class);
    private final SchedulerMatchPoolClaimService claim = mock(SchedulerMatchPoolClaimService.class);
    private final MatchingBatchReader reader = mock(MatchingBatchReader.class);
    private final MatchGroupComposer composer = mock(MatchGroupComposer.class);
    private final MatchProposalCreationService creation = mock(MatchProposalCreationService.class);
    private final MatchPoolReleaseService release = mock(MatchPoolReleaseService.class);
    private final MatchCollectionWindowService windows = mock(MatchCollectionWindowService.class);
    private final MatchPoolRepository pools = mock(MatchPoolRepository.class);
    private MatchingOrchestrationService service;

    @BeforeEach void setUp() {
        MatchingSchedulerProperties properties = new MatchingSchedulerProperties(false,
                Duration.ofSeconds(10), Duration.ofSeconds(2), Duration.ofSeconds(10),
                Duration.ofSeconds(30), Duration.ofSeconds(30), 20);
        service = new MatchingOrchestrationService(CLOCK, () -> BASE_TOKEN, properties, cleanup, claim,
                reader, composer, creation, release, windows, pools);
        when(pools.findFestivalIdsWithUnassignedPools(NOW)).thenReturn(List.of());
        when(release.release(anyString(), eq(NOW))).thenReturn(new MatchPoolReleaseResult(0));
        when(windows.completeEvaluation(anyLong(), anyLong(), anyString(), eq(NOW))).thenReturn(true);
    }

    @Test void 평가할_구간이_없으면_정리만_하고_종료한다() {
        when(windows.claimEvaluableWindows(NOW, 20, BASE_TOKEN)).thenReturn(List.of());

        MatchingOrchestrationResult result = service.runTick();

        assertThat(result.claimedCount()).isZero();
        assertThat(result.createdAttemptIds()).isEmpty();
        InOrder order = inOrder(cleanup, windows);
        // pool 잠금 해제가 구간 회수보다 먼저다. 순서가 뒤바뀌면 회수된 구간의 후보가 아직
        // LOCKED라 다음 실행의 조회에서 누락된다.
        order.verify(cleanup).cleanup(NOW, NOW.minusSeconds(30));
        order.verify(windows).releaseStaleEvaluations(NOW, NOW.minusSeconds(30));
        order.verify(windows).claimEvaluableWindows(NOW, 20, BASE_TOKEN);
        verify(claim, never()).claimWindow(anyLong(), any(), anyString());
    }

    @Test void 구간_평가에_실패하면_예외를_삼키고_구간을_종결하지_않는다() {
        when(windows.claimEvaluableWindows(NOW, 20, BASE_TOKEN))
                .thenReturn(List.of(new MatchCollectionWindowService.ClaimedWindow(WINDOW_ID, FESTIVAL_ID)));
        when(claim.claimWindow(WINDOW_ID, NOW, WINDOW_TOKEN))
                .thenThrow(new IllegalStateException("claim failed"));

        MatchingOrchestrationResult result = service.runTick();

        assertThat(result.failedGroupCount()).isOne();
        verify(release).release(WINDOW_TOKEN, NOW);
        // 종결하지 않아야 EVALUATING으로 남고 stale 회수가 다시 평가한다. 여기서 닫아버리면
        // 후보가 평가되지 않은 채 만료된다.
        verify(windows, never()).completeEvaluation(anyLong(), anyLong(), anyString(), any());
    }

    @Test void 정상_평가는_release_뒤에_구간을_종결한다() {
        givenSingleWindowWithCandidates();
        when(composer.compose(any(), any())).thenReturn(List.of());

        service.runTick();

        InOrder order = inOrder(release, windows);
        // 잔여 후보 이월은 종결과 같은 트랜잭션에서 일어난다. release가 먼저여야 잔여 후보가
        // WAITING으로 돌아와 이월 대상이 된다.
        order.verify(release).release(WINDOW_TOKEN, NOW);
        order.verify(windows).completeEvaluation(WINDOW_ID, FESTIVAL_ID, WINDOW_TOKEN, NOW);
    }

    @Test void 한_group_실패는_다른_group_생성을_막지_않는다() {
        givenSingleWindowWithCandidates();
        MatchGroupCombination first = mock(MatchGroupCombination.class);
        MatchGroupCombination second = mock(MatchGroupCombination.class);
        when(composer.compose(any(), any())).thenReturn(List.of(first, second));
        when(first.candidates()).thenReturn(List.of());
        when(creation.createInitial(eq(first), eq(WINDOW_TOKEN), eq(NOW), any()))
                .thenThrow(new IllegalStateException("group failed"));
        when(creation.createInitial(eq(second), eq(WINDOW_TOKEN), eq(NOW), any()))
                .thenReturn(new MatchProposalCreationResult(42L, List.of(1L)));

        MatchingOrchestrationResult result = service.runTick();

        assertThat(result.createdAttemptIds()).containsExactly(42L);
        assertThat(result.failedGroupCount()).isOne();
        verify(windows).completeEvaluation(WINDOW_ID, FESTIVAL_ID, WINDOW_TOKEN, NOW);
    }

    private void givenSingleWindowWithCandidates() {
        when(windows.claimEvaluableWindows(NOW, 20, BASE_TOKEN))
                .thenReturn(List.of(new MatchCollectionWindowService.ClaimedWindow(WINDOW_ID, FESTIVAL_ID)));
        when(claim.claimWindow(WINDOW_ID, NOW, WINDOW_TOKEN))
                .thenReturn(new MatchPoolClaimResult(WINDOW_TOKEN, List.of(1L)));
        when(reader.read(WINDOW_TOKEN))
                .thenReturn(new MatchingBatchReader.MatchingBatch(List.of(), Set.of(), Set.of()));
    }
}
