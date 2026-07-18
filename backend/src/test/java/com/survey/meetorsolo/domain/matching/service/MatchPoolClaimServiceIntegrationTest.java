package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@Testcontainers
@Sql(
        scripts = {
                "/fixtures/matching-engine-cleanup.sql",
                "/fixtures/matching-engine-foundation.sql"
        },
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class MatchPoolClaimServiceIntegrationTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final long FESTIVAL_ID = 9_100_001L;
    private static final long OTHER_FESTIVAL_ID = 9_100_002L;
    private static final long REQUESTER_MEMBER_ID = 9_110_001L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MatchPoolClaimService matchPoolClaimService;

    @Autowired
    private MatchPoolRepository matchPoolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 제한_개수만큼_후보를_선점하고_lock_정보를_저장한다() {
        String lockToken = "worker-1-token";

        MatchPoolClaimResult result = matchPoolClaimService.claimCandidates(
                FESTIVAL_ID,
                REQUESTER_MEMBER_ID,
                NOW,
                1,
                lockToken
        );

        assertThat(result.lockToken()).isEqualTo(lockToken);
        assertThat(result.poolIds()).containsExactly(9_120_011L);
        PoolState state = poolState(9_120_011L);
        assertThat(state.status()).isEqualTo(MatchPool.STATUS_LOCKED);
        assertThat(state.lockedAt()).isNotNull();
        assertThat(state.lockedAt().toInstant()).isEqualTo(NOW.toInstant());
        assertThat(state.lockToken()).isEqualTo(lockToken);
        assertThat(state.updatedAt().toInstant()).isEqualTo(NOW.toInstant());
        assertThat(countPoolsWithToken(lockToken)).isEqualTo(1);
    }

    @Test
    void 선점_개수는_limit을_초과하지_않고_기존_정렬을_유지한다() {
        MatchPoolClaimResult result = matchPoolClaimService.claimCandidates(
                FESTIVAL_ID,
                REQUESTER_MEMBER_ID,
                NOW,
                2,
                "limit-two-token"
        );

        assertThat(result.poolIds()).containsExactly(9_120_011L, 9_120_002L);
        assertThat(result.poolIds()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void 후보가_없으면_빈_결과를_반환한다() {
        MatchPoolClaimResult result = matchPoolClaimService.claimCandidates(
                OTHER_FESTIVAL_ID,
                9_110_004L,
                NOW,
                2,
                "empty-token"
        );

        assertThat(result.poolIds()).isEmpty();
    }

    @Test
    void 이미_LOCKED이거나_PROPOSED인_pool은_제외한다() {
        jdbcTemplate.update(
                """
                UPDATE match_pools
                SET status = 'LOCKED', locked_at = ?, lock_token = ?, updated_at = ?
                WHERE id = ?
                """,
                NOW.minusSeconds(1),
                "existing-lock",
                NOW.minusSeconds(1),
                9_120_011L
        );

        MatchPoolClaimResult result = matchPoolClaimService.claimCandidates(
                FESTIVAL_ID,
                REQUESTER_MEMBER_ID,
                NOW,
                5,
                "next-worker-token"
        );

        assertThat(result.poolIds()).containsExactly(9_120_002L);
        assertThat(result.poolIds()).doesNotContain(9_120_011L, 9_120_005L);
    }

    @Test
    void 상위_transaction이_rollback되면_WAITING과_null_lock_정보가_유지된다() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            MatchPoolClaimResult result = matchPoolClaimService.claimCandidates(
                    FESTIVAL_ID,
                    REQUESTER_MEMBER_ID,
                    NOW,
                    1,
                    "rollback-token"
            );
            assertThat(result.poolIds()).containsExactly(9_120_011L);
            status.setRollbackOnly();
        });

        PoolState state = poolState(9_120_011L);
        assertThat(state.status()).isEqualTo(MatchPool.STATUS_WAITING);
        assertThat(state.lockedAt()).isNull();
        assertThat(state.lockToken()).isNull();
        assertThat(state.updatedAt().toInstant()).isEqualTo(
                OffsetDateTime.of(2026, 7, 17, 14, 59, 40, 0, ZoneOffset.ofHours(9)).toInstant()
        );
    }

    @Test
    void limit은_양수여야_한다() {
        assertThatThrownBy(() -> claimWith(0, "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit은 1 이상이어야 합니다.");
        assertThatThrownBy(() -> claimWith(-1, "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit은 1 이상이어야 합니다.");
    }

    @Test
    void lockToken은_필수이고_100자_이하여야_한다() {
        assertThatThrownBy(() -> claimWith(1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockToken은 필수입니다.");
        assertThatThrownBy(() -> claimWith(1, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockToken은 필수입니다.");
        assertThatThrownBy(() -> claimWith(1, "a".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockToken은 100자 이하여야 합니다.");
    }

    @Test
    void 두_worker는_잠긴_pool을_기다리지_않고_서로_다른_pool을_선점한다() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstWorkerLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        AtomicReference<List<Long>> firstWorkerPoolIds = new AtomicReference<>();

        Future<List<Long>> firstWorker = workers.submit(() -> transactionTemplate.execute(status -> {
            List<MatchPool> pools = matchPoolRepository.findEligibleWaitingCandidatesForUpdate(
                    FESTIVAL_ID,
                    REQUESTER_MEMBER_ID,
                    NOW,
                    1
            );
            pools.forEach(pool -> pool.lock(NOW, "concurrent-worker-1"));
            entityManager.flush();
            firstWorkerPoolIds.set(pools.stream().map(MatchPool::getId).toList());
            firstWorkerLocked.countDown();
            await(releaseFirstWorker);
            return firstWorkerPoolIds.get();
        }));

        try {
            assertThat(firstWorkerLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<List<Long>> secondWorker = workers.submit(() -> transactionTemplate.execute(status -> {
                List<MatchPool> pools = matchPoolRepository.findEligibleWaitingCandidatesForUpdate(
                        FESTIVAL_ID,
                        REQUESTER_MEMBER_ID,
                        NOW,
                        1
                );
                pools.forEach(pool -> pool.lock(NOW, "concurrent-worker-2"));
                entityManager.flush();
                return pools.stream().map(MatchPool::getId).toList();
            }));

            List<Long> secondWorkerPoolIds = secondWorker.get(5, TimeUnit.SECONDS);

            assertThat(firstWorker.isDone()).isFalse();
            assertThat(firstWorkerPoolIds.get()).containsExactly(9_120_011L);
            assertThat(secondWorkerPoolIds).containsExactly(9_120_002L);
            assertThat(Set.copyOf(firstWorkerPoolIds.get()))
                    .doesNotContainAnyElementsOf(secondWorkerPoolIds);

            releaseFirstWorker.countDown();
            assertThat(firstWorker.get(5, TimeUnit.SECONDS)).containsExactly(9_120_011L);
        } finally {
            releaseFirstWorker.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MatchPoolClaimResult claimWith(int limit, String lockToken) {
        return matchPoolClaimService.claimCandidates(
                FESTIVAL_ID,
                REQUESTER_MEMBER_ID,
                NOW,
                limit,
                lockToken
        );
    }

    private PoolState poolState(long poolId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status, locked_at, lock_token, updated_at
                FROM match_pools
                WHERE id = ?
                """,
                (resultSet, rowNumber) -> new PoolState(
                        resultSet.getString("status"),
                        resultSet.getObject("locked_at", OffsetDateTime.class),
                        resultSet.getString("lock_token"),
                        resultSet.getObject("updated_at", OffsetDateTime.class)
                ),
                poolId
        );
    }

    private int countPoolsWithToken(String lockToken) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM match_pools WHERE lock_token = ?",
                Integer.class,
                lockToken
        );
        return count == null ? 0 : count;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 latch 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 thread가 중단되었습니다.", exception);
        }
    }

    private record PoolState(
            String status,
            OffsetDateTime lockedAt,
            String lockToken,
            OffsetDateTime updatedAt
    ) {
    }
}
