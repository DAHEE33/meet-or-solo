package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 수집 구간의 동시성·복구 경계를 검증한다.
 *
 * <p>{@code SKIP LOCKED}만으로는 "한 구간을 통째로 평가한다"가 보장되지 않는다. 여기서는 구간이
 * 여러 실행으로 쪼개지지 않는지, 소유권을 잃은 실행이 뒤늦게 돌아와 상태를 건드리지 못하는지,
 * 복구 순서 때문에 후보가 누락되지 않는지를 본다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        "app.matching.scoring.jaccard-weight=0.70",
        "app.matching.scoring.embedding-weight=0.30",
        "app.matching.scheduler.collect-window=10s",
        "app.matching.scheduler.matching-fixed-delay=2s",
        "app.matching.scheduler.stale-timeout=30s"
})
@Testcontainers
@Import(MatchingCollectionWindowConcurrencyIntegrationTest.MutableClockConfiguration.class)
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchingCollectionWindowConcurrencyIntegrationTest {

    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final long FESTIVAL = 9_100_001L;
    private static final long FIRST = 9_110_001L;
    private static final long SECOND = 9_110_002L;
    private static final long THIRD = 9_110_006L;
    private static final long FOURTH = 9_110_010L;
    private static final long FIFTH = 9_110_011L;
    private static final OffsetDateTime BASE_TIME =
            OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));

    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired MatchingOrchestrationService orchestration;
    @Autowired MatchPoolEntryService entryService;
    @Autowired MatchCollectionWindowService windowService;
    @Autowired SchedulerMatchPoolClaimService claimService;
    @Autowired MatchPoolCleanupService cleanupService;
    @Autowired MatchingBatchReader batchReader;
    @Autowired com.survey.meetorsolo.domain.matching.group.MatchGroupComposer composer;
    @Autowired MatchProposalCreationService creationService;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetScenario() {
        clock.reset(BASE_TIME.toInstant());
        jdbc.update("DELETE FROM match_attempt_members");
        jdbc.update("DELETE FROM match_proposals");
        jdbc.update("DELETE FROM match_attempts");
        jdbc.update("DELETE FROM match_pools");
        jdbc.update("DELETE FROM match_collection_windows");
        jdbc.update("DELETE FROM user_blocks");
        jdbc.update("DELETE FROM match_cooldowns");
        jdbc.update("DELETE FROM match_opponent_exclusions");
        jdbc.update("DELETE FROM member_preference_embeddings");
        prepareEmbeddings();
    }

    /**
     * 두 실행이 동시에 tick을 돌려도 한 구간의 후보가 쪼개지지 않는다.
     *
     * <p>쪼개졌다면 각 실행이 2~3명씩 나눠 평가해 점수 1·2위가 아닌 조합이 나온다. 결과가 5명을
     * 함께 비교했을 때와 같아야 한다.
     */
    @Test
    void 동시에_tick을_돌려도_한_구간의_후보가_쪼개지지_않는다() throws Exception {
        enterAll();
        advanceSeconds(2);

        List<MatchingOrchestrationResult> results = runConcurrently(
                () -> orchestration.runTick(), () -> orchestration.runTick());

        long totalAttempts = results.stream().mapToLong(result -> result.createdAttemptIds().size()).sum();
        assertThat(totalAttempts).as("results=%s", results).isEqualTo(2);
        assertThat(memberPairs()).as("5명을 함께 비교한 결과와 같아야 한다")
                .containsExactlyInAnyOrder(List.of(FIRST, FOURTH), List.of(SECOND, FIFTH));
        assertThat(poolStatus(THIRD)).isEqualTo("WAITING");
    }

    /**
     * 구간 합류와 수집 종료가 같은 시각에 일어나도 신청자가 누락되지 않는다.
     *
     * <p>수집 종료 시각에 들어온 신청자는 그 구간이 아니라 새 구간에 속한다. 둘 다 평가되어야
     * 하며, 어느 쪽도 구간 없이 남아서는 안 된다.
     */
    @Test
    void 구간_합류와_수집_종료가_같은_시각에_일어나도_신청자가_누락되지_않는다() throws Exception {
        enter(FIRST);
        advanceSeconds(2);
        enter(SECOND);
        advanceSeconds(8);                       // t=10, 수집 종료 시각과 정확히 같다

        // 종료 경계에서 신청과 평가가 동시에 일어난다.
        runConcurrently(() -> {
            enter(THIRD);
            return null;
        }, () -> orchestration.runTick());

        assertThat(unassignedPoolCount()).as("구간 없이 남은 후보가 있으면 평가되지 못한다").isZero();

        advanceSeconds(2);
        enter(FOURTH);
        advanceSeconds(11);
        orchestration.runTick();

        List<Long> matched = jdbc.queryForList(
                "SELECT DISTINCT member_id FROM match_attempt_members ORDER BY member_id", Long.class);
        assertThat(matched).as("네 명 모두 평가를 거쳐 매칭됐다")
                .containsExactly(FIRST, SECOND, THIRD, FOURTH);
    }

    /**
     * 소유권을 잃은 실행은 구간을 종결하지도, 잔여 후보를 옮기지도 못한다.
     *
     * <p>평가가 느려 stale 회수되면 그 구간은 다른 실행의 것이 된다. 원래 실행이 뒤늦게 돌아와
     * 종결해 버리면, 새 실행이 평가 중인 구간이 닫히고 후보가 어긋난다.
     */
    @Test
    void stale_회수된_뒤_소유권을_잃은_토큰으로는_종결도_이월도_못_한다() {
        enterAll();
        advanceSeconds(11);

        // 느린 실행이 구간을 잡았다.
        long windowId = windowService.claimEvaluableWindows(now(), 20, "slow-token").get(0).windowId();
        assertThat(windowStatus(windowId)).isEqualTo("EVALUATING");

        // stale 판정 시간이 지나 회수되고, 다른 실행이 가져갔다.
        advanceSeconds(31);
        windowService.releaseStaleEvaluations(now(), now().minusSeconds(30));
        assertThat(windowStatus(windowId)).isEqualTo("COLLECTED");
        windowService.claimEvaluableWindows(now(), 20, "fresh-token");
        assertThat(evaluatorToken(windowId)).isEqualTo("fresh-token");

        // 느린 실행이 뒤늦게 돌아왔다.
        boolean completed = windowService.completeEvaluation(windowId, FESTIVAL, "slow-token", now());

        assertThat(completed).isFalse();
        assertThat(windowStatus(windowId)).as("새 소유자의 평가가 유지된다").isEqualTo("EVALUATING");
        assertThat(evaluatorToken(windowId)).isEqualTo("fresh-token");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE collect_window_id <> ?", Integer.class, windowId))
                .as("이월도 일어나지 않았다").isZero();
    }

    /**
     * 소유권을 잃은 실행은 <b>제안도 만들 수 없다.</b>
     *
     * <p>종결·이월 차단만으로는 부족하다. 느린 실행이 stale 회수 전에 만들어 둔 조합을 들고
     * 뒤늦게 제안 생성까지 진행하면, 이미 다른 실행이 평가 중인 후보로 중복 제안이 생긴다.
     *
     * <p>차단 근거는 두 겹이다. stale 회수가 pool을 {@code WAITING}으로 되돌리고
     * {@code lock_token}을 비우므로 {@code MatchProposalCreationService}의 최종 검증이
     * "LOCKED가 아님"과 "lock_token 불일치" 양쪽에서 막는다.
     */
    @Test
    void stale_회수된_뒤_옛_토큰으로는_제안도_만들_수_없다() {
        enterAll();
        advanceSeconds(11);

        // 느린 실행이 구간과 후보를 잡고 조합까지 만들어 뒀다.
        MatchCollectionWindowService.ClaimedWindow window =
                windowService.claimEvaluableWindows(now(), 20, "slow-token").get(0);
        claimService.claimWindow(window.windowId(), now(), "slow-token");
        List<com.survey.meetorsolo.domain.matching.group.MatchGroupCombination> groups =
                composer.compose(batchReader.read("slow-token").candidates());
        assertThat(groups).as("조합이 있어야 이 검증이 성립한다").isNotEmpty();

        // 평가가 느려 stale 회수됐다. pool 잠금이 풀리고 구간도 평가 대기로 돌아간다.
        advanceSeconds(31);
        cleanupService.cleanup(now(), now().minusSeconds(30));
        windowService.releaseStaleEvaluations(now(), now().minusSeconds(30));

        // 느린 실행이 뒤늦게 옛 토큰으로 제안을 만들려 한다.
        assertThatThrownBy(() -> creationService.createInitial(
                groups.get(0), "slow-token", now(), Duration.ofSeconds(30)))
                .isInstanceOf(MatchProposalCreationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_attempts", Integer.class))
                .as("중간 데이터도 남지 않는다").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE lock_token='slow-token'", Integer.class))
                .isZero();
    }

    /**
     * 구간 회수보다 pool 잠금 해제가 먼저여서 남은 후보가 누락되지 않는다.
     *
     * <p>순서가 뒤바뀌면 회수된 구간을 새 실행이 가져가도 후보가 아직 {@code LOCKED}라 조회에서
     * 빠지고, 그만큼 적은 인원으로 평가된다.
     */
    @Test
    void 구간_회수와_pool_잠금_해제_순서_때문에_후보가_누락되지_않는다() {
        enterAll();
        advanceSeconds(11);

        // 죽은 실행이 구간과 후보를 모두 잡아둔 상태를 만든다.
        long windowId = windowService.claimEvaluableWindows(now(), 20, "dead-token").get(0).windowId();
        jdbc.update("UPDATE match_pools SET status='LOCKED', lock_token='dead-token', locked_at=? "
                + "WHERE collect_window_id=?", now(), windowId);

        advanceSeconds(31);
        MatchingOrchestrationResult result = orchestration.runTick();

        assertThat(result.claimedCount()).as("다섯 명 전원이 회수돼 함께 평가돼야 한다. result=%s", result)
                .isEqualTo(5);
        assertThat(result.createdAttemptIds()).hasSize(2);
        assertThat(memberPairs()).containsExactlyInAnyOrder(
                List.of(FIRST, FOURTH), List.of(SECOND, FIFTH));
    }

    private void enterAll() {
        enter(FIRST);
        advanceSeconds(2);
        enter(SECOND);
        advanceSeconds(2);
        enter(THIRD);
        advanceSeconds(2);
        enter(FOURTH);
        advanceSeconds(3);
        enter(FIFTH);
    }

    private void enter(long memberId) {
        entryService.enter(memberId, new MatchPoolEntryRequest(FESTIVAL, 2, true, List.of()));
    }

    private <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private void advanceSeconds(long seconds) {
        clock.advance(Duration.ofSeconds(seconds));
    }

    private String windowStatus(long windowId) {
        return jdbc.queryForObject("SELECT status FROM match_collection_windows WHERE id=?",
                String.class, windowId);
    }

    private String evaluatorToken(long windowId) {
        return jdbc.queryForObject("SELECT evaluator_token FROM match_collection_windows WHERE id=?",
                String.class, windowId);
    }

    private int unassignedPoolCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE collect_window_id IS NULL AND status='WAITING'",
                Integer.class);
    }

    private String poolStatus(long memberId) {
        return jdbc.queryForObject(
                "SELECT status FROM match_pools WHERE member_id=? ORDER BY id DESC LIMIT 1",
                String.class, memberId);
    }

    private List<List<Long>> memberPairs() {
        return jdbc.queryForList("SELECT id FROM match_attempts ORDER BY id", Long.class).stream()
                .map(attemptId -> jdbc.queryForList(
                        "SELECT member_id FROM match_attempt_members WHERE attempt_id=? ORDER BY member_id",
                        Long.class, attemptId))
                .toList();
    }

    private void prepareEmbeddings() {
        insertEmbedding(FIRST, vectorLiteral("1", "0"));
        insertEmbedding(SECOND, vectorLiteral("0", "1"));
        insertEmbedding(THIRD, vectorLiteral("0.70711", "0.70711"));
        insertEmbedding(FOURTH, vectorLiteral("0.99939", "0.03490"));
        insertEmbedding(FIFTH, vectorLiteral("0.08716", "0.99619"));
    }

    private void insertEmbedding(long memberId, String vector) {
        jdbc.update("INSERT INTO member_preference_embeddings("
                        + "member_id,preference_text,embedding,embedding_model,embedding_status,created_at,updated_at) "
                        + "VALUES (?,?,?::vector,?,'COMPLETED',?,?)",
                memberId, "검증용 취향 원문", vector, "text-embedding-3-small", BASE_TIME, BASE_TIME);
    }

    private String vectorLiteral(String first, String second) {
        StringBuilder literal = new StringBuilder(EMBEDDING_DIMENSIONS * 3)
                .append('[').append(first).append(',').append(second);
        for (int index = 2; index < EMBEDDING_DIMENSIONS; index++) {
            literal.append(",0");
        }
        return literal.append(']').toString();
    }

    static class MutableClock extends Clock {
        private final ZoneId zone = ZoneId.of("Asia/Seoul");
        private volatile Instant instant = Instant.parse("2026-07-17T06:00:00Z");

        void reset(Instant value) {
            this.instant = value;
        }

        void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override public ZoneId getZone() {
            return zone;
        }

        @Override public Clock withZone(ZoneId targetZone) {
            return this;
        }

        @Override public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {
        @Bean @Primary MutableClock mutableMatchingClock() {
            return new MutableClock();
        }
    }
}
