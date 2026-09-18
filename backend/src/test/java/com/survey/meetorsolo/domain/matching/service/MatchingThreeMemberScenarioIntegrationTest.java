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

/**
 * dev 서버에서 "3명이 같은 희망 인원으로 동시에 신청했는데 아무 일도 일어나지 않는다"고 보고된
 * 상황을 코드 경로로 재현한다.
 *
 * <p>scheduler tick 경로와 pool entry trigger 경로를 모두 태운다. 두 경로 모두 proposal을
 * 만들면 조합 로직에는 문제가 없고 원인이 배포 환경에 있다는 뜻이 된다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        "app.matching.scoring.jaccard-weight=0.70",
        "app.matching.scoring.embedding-weight=0.30"
})
@Testcontainers
@Import(MatchingThreeMemberScenarioIntegrationTest.FixedInputs.class)
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchingThreeMemberScenarioIntegrationTest {

    private static final String TOKEN = "three-member-fixed-token";
    /** 같은 축제(9100001)에 유효한 ACTIVE check-in을 가진 회원 3명. */
    private static final String TRIO = "9110001,9110002,9110006";

    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired MatchingOrchestrationService schedulerOrchestration;
    @Autowired PoolEntryMatchingOrchestrationService poolEntryOrchestration;
    @Autowired JdbcTemplate jdbc;

    @Test void 희망인원_3명인_회원_3명이_대기하면_scheduler_tick이_proposal을_만든다() {
        prepareTrio(3);

        MatchingOrchestrationResult result = schedulerOrchestration.runTick();

        assertThat(result.createdAttemptIds()).as("result=%s", result).hasSize(1);
        assertThat(result.failedGroupCount()).as("result=%s", result).isZero();
        assertProposedTrio(result.createdAttemptIds().get(0), 3);
    }

    @Test void 희망인원_4명인_회원_3명은_scheduler_tick에서_그룹이_되지_않는다() {
        prepareTrio(4);

        MatchingOrchestrationResult result = schedulerOrchestration.runTick();

        assertThat(result.createdAttemptIds()).as("result=%s", result).isEmpty();
        assertThat(result.claimedCount()).as("result=%s", result).isEqualTo(3);
        assertThat(result.releasedCount()).as("result=%s", result).isEqualTo(3);
    }

    @Test void 희망인원_3명인_세번째_회원의_pool_entry가_즉시_proposal을_만든다() {
        prepareTrio(3);

        MatchingOrchestrationResult result = poolEntryOrchestration.run(9_120_006L, 9_110_006L, 9_100_001L);

        assertThat(result.createdAttemptIds()).as("result=%s", result).hasSize(1);
        assertThat(result.failedGroupCount()).as("result=%s", result).isZero();
        assertProposedTrio(result.createdAttemptIds().get(0), 3);
    }

    /**
     * 회원 3명만 WAITING으로 남기고 차단·쿨타임·거절 이력을 모두 비운다.
     * fixture는 9110001-9110006 차단과 9110007 쿨타임을 넣어 두므로 그대로 두면 후보가 모자란다.
     */
    private void prepareTrio(int preferredGroupSize) {
        jdbc.update("DELETE FROM user_blocks");
        jdbc.update("DELETE FROM match_cooldowns");
        jdbc.update("DELETE FROM match_opponent_exclusions");
        jdbc.update("UPDATE match_pools SET status='PROPOSED' WHERE member_id NOT IN (" + TRIO + ")");
        jdbc.update("UPDATE match_pools SET preferred_group_size=?, status='WAITING' "
                + "WHERE member_id IN (" + TRIO + ")", preferredGroupSize);
    }

    private void assertProposedTrio(long attemptId, int targetGroupSize) {
        assertThat(jdbc.queryForObject(
                "SELECT target_group_size FROM match_attempts WHERE id=?", Integer.class, attemptId))
                .isEqualTo(targetGroupSize);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_proposals WHERE attempt_id=? AND proposal_round=1",
                Integer.class, attemptId))
                .isEqualTo(targetGroupSize);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE status='PROPOSED' AND member_id IN (" + TRIO + ")",
                Integer.class))
                .isEqualTo(targetGroupSize);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedInputs {
        @Bean @Primary Clock fixedMatchingClock() {
            return Clock.fixed(Instant.parse("2026-07-17T06:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
        @Bean @Primary MatchingLockTokenGenerator fixedMatchingTokenGenerator() { return () -> TOKEN; }
    }
}
