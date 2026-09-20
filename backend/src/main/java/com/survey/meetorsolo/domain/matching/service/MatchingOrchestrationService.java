package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchGroupComposer;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 수집 구간 단위로 후보를 평가한다.
 *
 * <p>예전에는 tick마다 "지금 대기 중인 후보 전체"를 평가했다. 그러면 유효한 조합이 처음 생기는
 * 순간 소진돼 대기 후보가 2명을 넘지 못하고, 조합이 1개뿐이면 정렬해서 고를 대상이 없으므로
 * 궁합 점수가 순위에 개입할 수 없었다. 지금은 축제별 수집 구간이 끝난 뒤에만 평가한다.
 *
 * <p>한 구간은 반드시 한 실행이 통째로 평가한다. 구간 확보에만 {@code SKIP LOCKED}를 쓰고, 구간
 * 안의 후보는 전량 잠근다.
 */
@Service
public class MatchingOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(MatchingOrchestrationService.class);
    /** 조합 계산이 이 시간을 넘으면 후보 수와 함께 남긴다. 비교 후보 상한을 정할 실측 근거다. */
    private static final Duration COMPOSE_SLOW_THRESHOLD = Duration.ofMillis(200);

    private final Clock clock;
    private final MatchingLockTokenGenerator tokenGenerator;
    private final MatchingSchedulerProperties properties;
    private final MatchPoolCleanupService cleanupService;
    private final SchedulerMatchPoolClaimService claimService;
    private final MatchingBatchReader batchReader;
    private final MatchGroupComposer groupComposer;
    private final MatchProposalCreationService creationService;
    private final MatchPoolReleaseService releaseService;
    private final MatchCollectionWindowService windowService;
    private final MatchPoolRepository matchPoolRepository;

    public MatchingOrchestrationService(Clock clock, MatchingLockTokenGenerator tokenGenerator,
            MatchingSchedulerProperties properties, MatchPoolCleanupService cleanupService,
            SchedulerMatchPoolClaimService claimService, MatchingBatchReader batchReader,
            MatchGroupComposer groupComposer, MatchProposalCreationService creationService,
            MatchPoolReleaseService releaseService, MatchCollectionWindowService windowService,
            MatchPoolRepository matchPoolRepository) {
        this.clock = clock; this.tokenGenerator = tokenGenerator; this.properties = properties;
        this.cleanupService = cleanupService; this.claimService = claimService; this.batchReader = batchReader;
        this.groupComposer = groupComposer; this.creationService = creationService;
        this.releaseService = releaseService; this.windowService = windowService;
        this.matchPoolRepository = matchPoolRepository;
    }

    public MatchingOrchestrationResult runTick() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime staleBefore = now.minus(properties.staleTimeout());
        String baseToken = tokenGenerator.generate();

        cleanupService.cleanup(now, staleBefore);
        // 평가 도중 죽어 EVALUATING에 남은 구간을 되돌린다. 재평가해도 이미 제안된 회원은
        // pool이 PROPOSED라 후보에서 빠지므로 중복 제안이 생기지 않는다.
        windowService.releaseStaleEvaluations(now, staleBefore);
        adoptPoolsWithoutWindow(now);

        List<MatchCollectionWindowService.ClaimedWindow> windows =
                windowService.claimEvaluableWindows(now, properties.batchSize(), baseToken);
        if (windows.isEmpty()) {
            return new MatchingOrchestrationResult(baseToken, 0, List.of(), 0, 0);
        }

        int claimedCount = 0;
        int failedGroups = 0;
        int releasedCount = 0;
        List<Long> attemptIds = new ArrayList<>();

        for (MatchCollectionWindowService.ClaimedWindow window : windows) {
            // 구간마다 다른 token을 쓴다. batchReader와 release가 token 단위로 동작하므로,
            // 같은 token을 공유하면 서로 다른 구간의 후보가 한 배치로 섞인다.
            String token = baseToken + ":" + window.windowId();
            boolean evaluated = false;
            try {
                MatchPoolClaimResult claim = claimService.claimWindow(window.windowId(), now, token);
                claimedCount += claim.poolIds().size();
                if (!claim.poolIds().isEmpty()) {
                    failedGroups += evaluateWindow(token, now, claim.poolIds().size(), attemptIds);
                }
                evaluated = true;
            } catch (RuntimeException exception) {
                // 구간을 EVALUATING으로 남겨 둔다. stale 회수가 COLLECTED로 되돌려 다음 tick이
                // 다시 평가한다. 여기서 종결해 버리면 후보가 평가되지 않은 채 만료된다.
                failedGroups++;
                log.error("수집 구간 평가에 실패했습니다. windowId={}, token={}",
                        window.windowId(), token, exception);
            } finally {
                try {
                    // release가 먼저다. 잔여 후보가 WAITING으로 돌아온 뒤에야 다음 구간으로
                    // 옮길 수 있다. 순서가 뒤바뀌면 LOCKED인 후보가 이월에서 누락된다.
                    releasedCount += releaseService.release(token, now).releasedCount();
                    if (evaluated) {
                        // 소유권을 잃었으면(느린 평가가 stale 회수된 경우) 종결도 이월도 하지 않는다.
                        windowService.completeEvaluation(window.windowId(), window.festivalId(), token, now);
                    }
                } catch (RuntimeException closeFailure) {
                    log.error("수집 구간 종료 처리에 실패했습니다. windowId={}, token={}",
                            window.windowId(), token, closeFailure);
                }
            }
        }
        return new MatchingOrchestrationResult(baseToken, claimedCount, attemptIds, failedGroups, releasedCount);
    }

    /** @return 실패한 그룹 수 */
    private int evaluateWindow(String token, OffsetDateTime now, int candidateCount, List<Long> attemptIds) {
        MatchingBatchReader.MatchingBatch batch = batchReader.read(token);
        long startedNanos = System.nanoTime();
        List<MatchGroupCombination> groups = groupComposer.compose(batch.candidates(), (left, right) ->
                !batch.blockedPairs().contains(MatchingBatchReader.MemberPair.of(left.memberId(), right.memberId()))
                && !batch.excludedPairs().contains(MatchOpponentPair.of(
                        left.memberId(), left.checkinId(), right.memberId(), right.checkinId())));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
        if (elapsed.compareTo(COMPOSE_SLOW_THRESHOLD) > 0) {
            // 조합은 후보 수에 대해 빠르게 증가한다. 비교 후보 상한을 정하려면 실측이 필요하다.
            log.warn("조합 계산이 오래 걸렸습니다. candidates={}, elapsedMs={}",
                    candidateCount, elapsed.toMillis());
        }

        int failedGroups = 0;
        for (MatchGroupCombination group : groups) {
            try {
                attemptIds.add(creationService.createInitial(
                        group, token, now, properties.proposalTimeout()).attemptId());
            } catch (RuntimeException exception) {
                failedGroups++;
                log.warn("매칭 그룹 proposal 생성에 실패했습니다. token={}, poolIds={}", token,
                        group.candidates().stream().map(candidate -> candidate.poolId()).toList(), exception);
            }
        }
        return failedGroups;
    }

    /**
     * 수집 구간이 없는 대기 후보를 편입한다.
     *
     * <p>이 기능 배포 시점에 이미 대기 중이던 pool에는 구간이 없다. 그대로 두면 어느 구간에도
     * 속하지 않아 평가되지 못한 채 만료된다.
     */
    private void adoptPoolsWithoutWindow(OffsetDateTime now) {
        for (Long festivalId : matchPoolRepository.findFestivalIdsWithUnassignedPools(now)) {
            try {
                long windowId = windowService.nextWindow(festivalId, now).getId();
                int adopted = matchPoolRepository.assignWindowToUnassignedPools(windowId, festivalId, now);
                if (adopted > 0) {
                    log.info("수집 구간이 없던 대기 후보를 편입했습니다. festivalId={}, windowId={}, count={}",
                            festivalId, windowId, adopted);
                }
            } catch (RuntimeException exception) {
                log.warn("대기 후보 구간 편입에 실패했습니다. festivalId={}", festivalId, exception);
            }
        }
    }
}
