package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers
@Import(MatchingOrchestrationServiceIntegrationTest.FixedInputs.class)
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchingOrchestrationServiceIntegrationTest {
    private static final String TOKEN = "orchestration-fixed-token";
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Autowired MatchingOrchestrationService service;
    @Autowired JdbcTemplate jdbc;

    @Test void 그룹_생성실패후_token_owned_LOCKED를_즉시_release한다() {
        installMemberFailureTrigger();
        MatchingOrchestrationResult result;
        try { result = service.runTick(); } finally { dropMemberFailureTrigger(); }
        assertThat(result.failedGroupCount()).isOne();
        assertThat(result.createdAttemptIds()).isEmpty();
        assertThat(result.releasedCount()).isEqualTo(result.claimedCount()).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE lock_token=?", Integer.class, TOKEN)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE status='LOCKED'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_attempts WHERE started_at='2026-07-17T15:00:00+09:00'", Integer.class)).isZero();
    }

    private void installMemberFailureTrigger() {
        jdbc.execute("CREATE OR REPLACE FUNCTION test_orchestration_member_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced test failure'; END $$");
        jdbc.execute("CREATE TRIGGER test_orchestration_member BEFORE INSERT ON match_attempt_members FOR EACH ROW EXECUTE FUNCTION test_orchestration_member_fn()");
    }
    private void dropMemberFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_orchestration_member ON match_attempt_members");
        jdbc.execute("DROP FUNCTION IF EXISTS test_orchestration_member_fn()");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedInputs {
        @Bean @Primary Clock fixedMatchingClock() {
            return Clock.fixed(Instant.parse("2026-07-17T06:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
        @Bean @Primary MatchingLockTokenGenerator fixedMatchingTokenGenerator() { return () -> TOKEN; }
    }
}
