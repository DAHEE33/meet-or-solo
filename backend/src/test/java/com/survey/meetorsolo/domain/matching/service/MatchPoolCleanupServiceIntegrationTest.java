package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        "spring.jpa.show-sql=false",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
@Sql(
        scripts = {
                "/fixtures/matching-engine-cleanup.sql",
                "/fixtures/matching-engine-foundation.sql"
        },
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class MatchPoolCleanupServiceIntegrationTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime STALE_BEFORE = NOW.minusSeconds(30);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MatchPoolCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 만료된_WAITING만_EXPIRED로_전환하고_정상_WAITING은_보존한다() {
        setSearchExpiresAt(9_120_003L, NOW);

        MatchPoolCleanupResult result = cleanupService.cleanup(NOW, STALE_BEFORE);

        assertThat(result.expiredWaitingCount()).isEqualTo(1);
        assertThat(poolState(9_120_003L).status()).isEqualTo("EXPIRED");
        assertThat(poolState(9_120_002L).status()).isEqualTo("WAITING");
    }

    @Test
    void stale_LOCKED는_유효하면_WAITING으로_회수하고_만료됐으면_EXPIRED로_전환한다() {
        lockPool(9_120_002L, STALE_BEFORE, "stale-valid");
        lockPool(9_120_003L, STALE_BEFORE, "stale-expired");

        MatchPoolCleanupResult result = cleanupService.cleanup(NOW, STALE_BEFORE);

        assertThat(result.expiredStaleLockedCount()).isEqualTo(1);
        assertThat(result.releasedStaleLockedCount()).isEqualTo(1);
        assertClearedState(9_120_002L, "WAITING");
        assertClearedState(9_120_003L, "EXPIRED");
    }

    @Test
    void 최신_LOCKED와_lock_정보가_불완전한_LOCKED는_보존한다() {
        lockPool(9_120_002L, STALE_BEFORE.plusSeconds(1), "fresh-lock");
        jdbcTemplate.update(
                "UPDATE match_pools SET status = 'LOCKED', locked_at = ?, lock_token = NULL WHERE id = ?",
                STALE_BEFORE.minusSeconds(1),
                9_120_011L
        );
        jdbcTemplate.update(
                "UPDATE match_pools SET status = 'LOCKED', locked_at = NULL, lock_token = ? WHERE id = ?",
                "missing-locked-at",
                9_120_006L
        );

        MatchPoolCleanupResult result = cleanupService.cleanup(NOW, STALE_BEFORE);

        assertThat(result.totalChangedCount()).isEqualTo(1);
        assertThat(poolState(9_120_002L).status()).isEqualTo("LOCKED");
        assertThat(poolState(9_120_002L).lockToken()).isEqualTo("fresh-lock");
        assertThat(poolState(9_120_011L).status()).isEqualTo("LOCKED");
        assertThat(poolState(9_120_011L).lockedAt()).isNotNull();
        assertThat(poolState(9_120_011L).lockToken()).isNull();
        assertThat(poolState(9_120_006L).status()).isEqualTo("LOCKED");
        assertThat(poolState(9_120_006L).lockedAt()).isNull();
        assertThat(poolState(9_120_006L).lockToken()).isEqualTo("missing-locked-at");
    }

    @Test
    void 정리_작업을_반복하면_두번째_실행은_추가_변경이_없다() {
        lockPool(9_120_002L, STALE_BEFORE, "stale-valid");

        MatchPoolCleanupResult first = cleanupService.cleanup(NOW, STALE_BEFORE);
        MatchPoolCleanupResult second = cleanupService.cleanup(NOW, STALE_BEFORE);

        assertThat(first.totalChangedCount()).isEqualTo(2);
        assertThat(second.totalChangedCount()).isZero();
    }

    @Test
    void 기준_시각은_필수이고_staleBefore는_now_이후일_수_없다() {
        assertThatThrownBy(() -> cleanupService.cleanup(null, STALE_BEFORE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now는 필수입니다.");
        assertThatThrownBy(() -> cleanupService.cleanup(NOW, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("staleBefore는 필수입니다.");
        assertThatThrownBy(() -> cleanupService.cleanup(NOW, NOW.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("staleBefore는 now 이후일 수 없습니다.");
    }

    @Test
    void 상위_transaction이_rollback되면_정리한_상태도_원복된다() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            MatchPoolCleanupResult result = cleanupService.cleanup(NOW, STALE_BEFORE);
            assertThat(result.expiredWaitingCount()).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(poolState(9_120_003L).status()).isEqualTo("WAITING");
    }

    private void setSearchExpiresAt(long poolId, OffsetDateTime searchExpiresAt) {
        jdbcTemplate.update(
                "UPDATE match_pools SET search_expires_at = ? WHERE id = ?",
                searchExpiresAt,
                poolId
        );
    }

    private void lockPool(long poolId, OffsetDateTime lockedAt, String lockToken) {
        jdbcTemplate.update(
                "UPDATE match_pools SET status = 'LOCKED', locked_at = ?, lock_token = ? WHERE id = ?",
                lockedAt,
                lockToken,
                poolId
        );
    }

    private void assertClearedState(long poolId, String status) {
        PoolState state = poolState(poolId);
        assertThat(state.status()).isEqualTo(status);
        assertThat(state.lockedAt()).isNull();
        assertThat(state.lockToken()).isNull();
        assertThat(state.updatedAt().toInstant()).isEqualTo(NOW.toInstant());
    }

    private PoolState poolState(long poolId) {
        return jdbcTemplate.queryForObject(
                "SELECT status, locked_at, lock_token, updated_at FROM match_pools WHERE id = ?",
                (resultSet, rowNumber) -> new PoolState(
                        resultSet.getString("status"),
                        resultSet.getObject("locked_at", OffsetDateTime.class),
                        resultSet.getString("lock_token"),
                        resultSet.getObject("updated_at", OffsetDateTime.class)
                ),
                poolId
        );
    }

    private record PoolState(
            String status,
            OffsetDateTime lockedAt,
            String lockToken,
            OffsetDateTime updatedAt
    ) {
    }
}
