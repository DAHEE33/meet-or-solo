package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import jakarta.persistence.EntityManager;
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

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class SchedulerMatchPoolClaimServiceIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Autowired SchedulerMatchPoolClaimService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MatchPoolRepository repository;
    @Autowired EntityManager entityManager;

    @Test void 제한된_WAITING만_동일_token과_시각으로_선점한다() {
        MatchPoolClaimResult result = service.claim(NOW, 2, "scheduler-token");
        assertThat(result.poolIds()).containsExactly(9_120_011L, 9_120_001L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE status='LOCKED' AND lock_token=? AND locked_at=?",
                Integer.class, "scheduler-token", NOW)).isEqualTo(2);
    }

    @Test void 만료_pool_checkin과_cooldown_및_비_WAITING은_제외한다() {
        MatchPoolClaimResult result = service.claim(NOW, 20, "filter-token");
        assertThat(result.poolIds()).doesNotContain(9_120_003L, 9_120_005L, 9_120_007L, 9_120_008L, 9_120_009L);
    }

    @Test void 상위_transaction_rollback이면_선점도_rollback된다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(service.claim(NOW, 1, "rollback-token").poolIds()).isNotEmpty();
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE lock_token='rollback-token'", Integer.class)).isZero();
    }

    @Test void 동시_worker는_같은_pool을_중복_선점하지_않는다() throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<List<Long>> firstIds = new AtomicReference<>();
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            var first = workers.submit(() -> template.execute(status -> {
                List<MatchPool> pools = repository.findSchedulerClaimablePoolsForUpdate(NOW, 1);
                pools.forEach(pool -> pool.lock(NOW, "worker-a"));
                entityManager.flush();
                firstIds.set(pools.stream().map(MatchPool::getId).toList());
                firstLocked.countDown();
                await(releaseFirst);
                return firstIds.get();
            }));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> service.claim(NOW, 1, "worker-b").poolIds());
            List<Long> secondIds = second.get(5, TimeUnit.SECONDS);
            assertThat(first.isDone()).isFalse();
            assertThat(firstIds.get()).containsExactly(9_120_011L);
            assertThat(secondIds).containsExactly(9_120_001L);
            assertThat(Set.copyOf(firstIds.get())).doesNotContainAnyElementsOf(secondIds);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE lock_token='worker-b'", Integer.class)).isOne();
            releaseFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(9_120_011L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE lock_token='worker-a'", Integer.class)).isOne();
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
