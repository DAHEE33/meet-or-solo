package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

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

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate") @Testcontainers
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchPoolReleaseServiceIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Autowired MatchPoolReleaseService service; @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void 소유한_LOCKED만_유효성에_따라_WAITING과_EXPIRED로_release한다() {
        lock(9_120_002L, "mine"); lock(9_120_003L, "mine"); lock(9_120_011L, "other");
        assertThat(service.release("mine", NOW).releasedCount()).isEqualTo(2);
        assertState(9_120_002L, "WAITING", null); assertState(9_120_003L, "EXPIRED", null);
        assertState(9_120_011L, "LOCKED", "other");
        assertThat(service.release("mine", NOW).releasedCount()).isZero();
    }

    @Test void 같은_token이어도_PROPOSED는_보존한다() {
        lock(9_120_002L, "mine");
        jdbc.update("UPDATE match_pools SET status='PROPOSED' WHERE id=9120002");
        assertThat(service.release("mine", NOW).releasedCount()).isZero();
        assertState(9_120_002L, "PROPOSED", "mine");
    }

    @Test void 외부_transaction이_rollback되면_release도_원복된다() {
        lock(9_120_002L, "mine");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(service.release("mine", NOW).releasedCount()).isOne();
            status.setRollbackOnly();
        });
        assertState(9_120_002L, "LOCKED", "mine");
    }
    private void lock(long id, String token) { jdbc.update("UPDATE match_pools SET status='LOCKED',locked_at=?,lock_token=? WHERE id=?", NOW, token, id); }
    private void assertState(long id, String status, String token) {
        assertThat(jdbc.queryForMap("SELECT status,lock_token,locked_at FROM match_pools WHERE id=?", id))
                .containsEntry("status", status).containsEntry("lock_token", token);
        if (token == null) assertThat(jdbc.queryForObject("SELECT locked_at IS NULL FROM match_pools WHERE id=?", Boolean.class, id)).isTrue();
    }
}
