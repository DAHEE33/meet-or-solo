package com.survey.meetorsolo.domain.safety.report;

import static com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportResponse;
import com.survey.meetorsolo.domain.safety.report.service.MatchReportService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=match-report-integration-test-secret",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(MatchReportIntegrationTest.FixedClockConfiguration.class)
@Sql(
        scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class MatchReportIntegrationTest {

    private static final long GROUP_ID = 9_170_001L;
    private static final long REPORTER_ID = 9_110_001L;
    private static final long REPORTED_ID = 9_110_002L;
    private static final long OUTSIDER_ID = 9_110_003L;
    private static final OffsetDateTime TEST_NOW = NOW.plusSeconds(10);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private MatchReportService service;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 진행_중인_group의_상대_신고를_생성하고_최소_snapshot만_반환한다() throws Exception {
        insertGroup("IN_PROGRESS", null, null, REPORTER_ID, REPORTED_ID);

        mockMvc.perform(post("/api/match-groups/{groupId}/reports", GROUP_ID)
                        .cookie(cookie(REPORTER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reporterMemberId":9110003,"reportedMemberId":9110002,"reasonCode":"RUDE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportId").isNumber())
                .andExpect(jsonPath("$.data.groupId").value(GROUP_ID))
                .andExpect(jsonPath("$.data.reportedMemberId").value(REPORTED_ID))
                .andExpect(jsonPath("$.data.reasonCode").value("RUDE"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.reporterMemberId").doesNotExist())
                .andExpect(jsonPath("$.data.detailEncrypted").doesNotExist());

        assertThat(reportCount()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT reporter_member_id FROM reports WHERE group_id=?",
                Long.class, GROUP_ID)).isEqualTo(REPORTER_ID);
        assertThat(jdbc.queryForObject(
                "SELECT detail_encrypted IS NULL FROM reports WHERE group_id=?",
                Boolean.class, GROUP_ID)).isTrue();
    }

    @Test
    void 본인_신고는_거절하고_row를_만들지_않는다() {
        insertGroup("CONFIRMED", null, null, REPORTER_ID, REPORTED_ID);

        assertThatThrownBy(() -> service.submit(
                REPORTER_ID, GROUP_ID, REPORTER_ID, MatchReportReasonCode.SAFETY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REPORT_INVALID_REQUEST));
        assertThat(reportCount()).isZero();
    }

    @Test
    void 신고자가_group_참여자가_아니면_존재를_숨기고_거절한다() {
        insertGroup("CONFIRMED", null, null, REPORTED_ID, OUTSIDER_ID);

        assertResourceNotFound(OUTSIDER_ID, REPORTER_ID);
    }

    @Test
    void 피신고자가_group_참여자가_아니면_존재를_숨기고_거절한다() {
        insertGroup("CONFIRMED", null, null, REPORTER_ID, OUTSIDER_ID);

        assertResourceNotFound(REPORTER_ID, REPORTED_ID);
    }

    @Test
    void 임의_group_ID를_사용한_IDOR은_404로_거절한다() throws Exception {
        insertGroup("CONFIRMED", null, null, REPORTED_ID, OUTSIDER_ID);

        mockMvc.perform(post("/api/match-groups/{groupId}/reports", GROUP_ID)
                        .cookie(cookie(REPORTER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportedMemberId":9110002,"reasonCode":"SAFETY"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REPORT_RESOURCE_NOT_FOUND"));
        assertThat(reportCount()).isZero();
    }

    @Test
    void 허용되지_않은_reasonCode는_공통_enum_validation으로_거절한다() throws Exception {
        insertGroup("CONFIRMED", null, null, REPORTER_ID, REPORTED_ID);

        mockMvc.perform(post("/api/match-groups/{groupId}/reports", GROUP_ID)
                        .cookie(cookie(REPORTER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportedMemberId":9110002,"reasonCode":"FREE_TEXT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
        assertThat(reportCount()).isZero();
    }

    @Test
    void 동일_요청_반복은_같은_snapshot을_반환하고_status를_초기화하지_않는다() {
        insertGroup("CONFIRMED", null, null, REPORTER_ID, REPORTED_ID);

        MatchReportResponse first = submit();
        jdbc.update("UPDATE reports SET status='REVIEWING' WHERE id=?", first.reportId());
        MatchReportResponse repeated = submit();

        assertThat(repeated.reportId()).isEqualTo(first.reportId());
        assertThat(repeated.createdAt()).isEqualTo(first.createdAt());
        assertThat(repeated.status()).isEqualTo("REVIEWING");
        assertThat(reportCount()).isOne();
    }

    @Test
    void 동시_동일_요청도_UNIQUE와_transaction으로_row_한_건을_유지한다() throws Exception {
        insertGroup("CONFIRMED", null, null, REPORTER_ID, REPORTED_ID);
        int workers = 6;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MatchReportResponse>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return submit();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> ids = new ArrayList<>();
            for (Future<MatchReportResponse> future : futures) {
                ids.add(future.get(10, TimeUnit.SECONDS).reportId());
            }
            assertThat(ids).containsOnly(ids.get(0));
            assertThat(reportCount()).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 종료_후_30일_직전은_허용한다() {
        insertGroup("COMPLETED", TEST_NOW.minusDays(30).plusSeconds(1), null,
                REPORTER_ID, REPORTED_ID);

        assertThat(submit().status()).isEqualTo("SUBMITTED");
    }

    @Test
    void 종료_후_정확히_30일_경계는_허용한다() {
        insertGroup("CANCELLED", null, TEST_NOW.minusDays(30), REPORTER_ID, REPORTED_ID);

        assertThat(submit().status()).isEqualTo("SUBMITTED");
    }

    @Test
    void 종료_후_30일_초과는_거절한다() {
        insertGroup("COMPLETED", TEST_NOW.minusDays(30).minusSeconds(1), null,
                REPORTER_ID, REPORTED_ID);

        assertThatThrownBy(this::submit)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REPORT_WINDOW_EXPIRED));
        assertThat(reportCount()).isZero();
    }

    @Test
    void 신고_후_penalty_cooldown_회원_점수와_매너온도는_변하지_않는다() {
        insertGroup("IN_PROGRESS", null, null, REPORTER_ID, REPORTED_ID);
        NumberSnapshot before = numberSnapshot();

        submit();

        assertThat(numberSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_events WHERE group_id=?",
                Integer.class, GROUP_ID)).isZero();
    }

    @Test
    void report_insert_실패는_부분_저장_없이_rollback한다() {
        insertGroup("CONFIRMED", null, null, REPORTER_ID, REPORTED_ID);
        jdbc.execute("""
                CREATE FUNCTION fail_report_insert() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced report failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_report_insert
                BEFORE INSERT ON reports
                FOR EACH ROW EXECUTE FUNCTION fail_report_insert()
                """);
        try {
            assertThatThrownBy(this::submit).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_report_insert ON reports");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_report_insert()");
        }

        assertThat(reportCount()).isZero();
    }

    private void assertResourceNotFound(long reporterId, long reportedId) {
        assertThatThrownBy(() -> service.submit(
                reporterId, GROUP_ID, reportedId, MatchReportReasonCode.SAFETY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REPORT_RESOURCE_NOT_FOUND));
        assertThat(reportCount()).isZero();
    }

    private MatchReportResponse submit() {
        return service.submit(REPORTER_ID, GROUP_ID, REPORTED_ID, MatchReportReasonCode.RUDE);
    }

    private void insertGroup(
            String status,
            OffsetDateTime completedAt,
            OffsetDateTime cancelledAt,
            long firstMemberId,
            long secondMemberId
    ) {
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count,
                    confirmed_at, completed_at, cancelled_at, created_at, updated_at
                ) VALUES (?, 9130001, 9100001, ?, 2, ?, ?, ?, ?, ?)
                """, GROUP_ID, status, NOW, completedAt, cancelledAt, NOW, NOW);
        jdbc.update("""
                INSERT INTO match_group_members(
                    id, group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES
                    (9180001, ?, ?, 'JOINED', true, ?, ?),
                    (9180002, ?, ?, 'JOINED', true, ?, ?)
                """, GROUP_ID, firstMemberId, NOW, NOW,
                GROUP_ID, secondMemberId, NOW, NOW);
    }

    private int reportCount() {
        return jdbc.queryForObject("SELECT count(*) FROM reports", Integer.class);
    }

    private NumberSnapshot numberSnapshot() {
        return jdbc.queryForObject("""
                SELECT
                    (SELECT count(*) FROM match_penalty_events) AS penalty_count,
                    (SELECT count(*) FROM match_cooldowns) AS cooldown_count,
                    penalty_score,
                    manner_temperature
                FROM members WHERE id = ?
                """, (rs, rowNum) -> new NumberSnapshot(
                rs.getInt("penalty_count"),
                rs.getInt("cooldown_count"),
                rs.getInt("penalty_score"),
                rs.getBigDecimal("manner_temperature")
        ), REPORTED_ID);
    }

    private jakarta.servlet.http.Cookie cookie(long memberId) {
        return new jakarta.servlet.http.Cookie(
                "access_token", jwtProvider.createAccessToken(memberId, "ACTIVE"));
    }

    private record NumberSnapshot(
            int penaltyCount,
            int cooldownCount,
            int penaltyScore,
            java.math.BigDecimal mannerTemperature
    ) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedReportClock() {
            return Clock.fixed(TEST_NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
