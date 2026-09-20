package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * 수집 구간 단위 선점을 검증한다.
 *
 * <p>{@code batch-size}를 2로 낮춰 두고 그보다 많은 후보를 넣는다. 구간 후보를 신청순으로 자르지
 * 않는다는 것이 이 설정의 목적이다. 조회 분할(batch-size)과 비교 후보 제한은 다른 문제다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        "app.matching.scheduler.batch-size=2"
})
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

    private long windowId;

    @BeforeEach void assignAllPoolsToOneWindow() {
        jdbc.update("DELETE FROM match_collection_windows");
        jdbc.update("INSERT INTO match_collection_windows"
                + "(festival_id,started_at,ends_at,status,created_at,updated_at) "
                + "VALUES (9100001,?,?,'COLLECTED',?,?)",
                NOW.minusSeconds(10), NOW, NOW.minusSeconds(10), NOW.minusSeconds(10));
        windowId = jdbc.queryForObject(
                "SELECT id FROM match_collection_windows ORDER BY id DESC LIMIT 1", Long.class);
        jdbc.update("UPDATE match_pools SET collect_window_id=?", windowId);
    }

    @Test void 구간_후보를_batch_size와_무관하게_전량_선점한다() {
        MatchPoolClaimResult result = service.claimWindow(windowId, NOW, "window-token");

        // batch-size가 2여도 잘리지 않는다. 신청순으로 자르면 뒤쪽 후보가 비교에서 빠진다.
        assertThat(result.poolIds()).hasSizeGreaterThan(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE status='LOCKED' AND lock_token=? AND locked_at=?",
                Integer.class, "window-token", NOW))
                .isEqualTo(result.poolIds().size());
    }

    @Test void 만료_pool_checkin과_cooldown_및_비_WAITING은_제외한다() {
        MatchPoolClaimResult result = service.claimWindow(windowId, NOW, "filter-token");

        assertThat(result.poolIds())
                .doesNotContain(9_120_003L, 9_120_005L, 9_120_007L, 9_120_008L, 9_120_009L);
    }

    @Test void 다른_구간의_후보는_선점하지_않는다() {
        jdbc.update("UPDATE match_pools SET collect_window_id=NULL WHERE member_id=9110001");

        MatchPoolClaimResult result = service.claimWindow(windowId, NOW, "other-window-token");

        assertThat(result.poolIds()).doesNotContain(9_120_001L);
    }

    @Test void 상위_transaction_rollback이면_선점도_rollback된다() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            service.claimWindow(windowId, NOW, "rollback-token");
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE lock_token=?", Integer.class, "rollback-token"))
                .isZero();
    }
}
