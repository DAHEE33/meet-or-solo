package com.survey.meetorsolo.domain.safety.report.admin;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.report.admin.dto.*;
import com.survey.meetorsolo.domain.safety.report.admin.service.AdminReportService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.jwt.secret=admin-report-integration-test-secret",
        "app.admin.report.cursor-hmac-secret=admin-report-cursor-test-secret-32-bytes",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(AdminReportIntegrationTest.FixedClockConfiguration.class)
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class AdminReportIntegrationTest {

    private static final long ADMIN_A = 9_110_010L;
    private static final long ADMIN_B = 9_110_011L;
    private static final long USER = 9_110_009L;
    private static final long REPORTER = 9_110_001L;
    private static final long REPORTED = 9_110_002L;
    private static final long GROUP = 9_170_001L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-15T12:00:00+09:00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired AdminReportService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @BeforeEach
    void prepare() {
        jdbc.update("UPDATE members SET role='ADMIN', nickname='admin-a' WHERE id=?", ADMIN_A);
        jdbc.update("UPDATE members SET role='ADMIN', nickname='admin-b' WHERE id=?", ADMIN_B);
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count, confirmed_at,
                    completed_at, created_at, updated_at
                ) VALUES (?, 9130001, 9100001, 'COMPLETED', 2, ?, ?, ?, ?)
                """, GROUP, NOW.minusHours(2), NOW.minusHours(1), NOW.minusHours(2), NOW.minusHours(1));
    }

    @Test
    void admin_me와_각_신고_API는_DB_role을_독립적으로_확인한다() throws Exception {
        long reportId = insertReport("SUBMITTED", "SAFETY", NOW.minusMinutes(1));
        mockMvc.perform(get("/api/admin/me").cookie(cookie(ADMIN_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
        mockMvc.perform(get("/api/admin/reports").cookie(cookie(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/reports/{id}", reportId).cookie(cookie(99_999_999L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void filter_기간_경계와_동일시각_ID_cursor가_누락과_중복을_만들지_않는다() {
        OffsetDateTime same = NOW.minusMinutes(10);
        List<Long> ids = List.of(
                insertReport(9_110_001L, "SUBMITTED", "SAFETY", same),
                insertReport(9_110_003L, "SUBMITTED", "SAFETY", same),
                insertReport(9_110_004L, "SUBMITTED", "SAFETY", same),
                insertReport(9_110_005L, "REVIEWING", "RUDE", same.minusSeconds(1)));

        AdminReportPageResponse first = service.list(ADMIN_A, "SUBMITTED", "SAFETY",
                same.toString(), NOW.toString(), null, 2);
        AdminReportPageResponse second = service.list(ADMIN_A, "SUBMITTED", "SAFETY",
                same.toString(), NOW.toString(), first.pagination().nextCursor(), 2);

        List<Long> actual = new ArrayList<>();
        actual.addAll(first.items().stream().map(AdminReportListItemResponse::reportId).toList());
        actual.addAll(second.items().stream().map(AdminReportListItemResponse::reportId).toList());
        assertThat(actual).containsExactly(ids.get(2), ids.get(1), ids.get(0));
        assertThat(actual).doesNotHaveDuplicates();
        assertThat(first.pagination().hasNext()).isTrue();
        assertThat(second.pagination().hasNext()).isFalse();
    }

    @Test
    void cursor_변조와_다른_filter_재사용은_400_오류다() {
        insertReport(9_110_001L, "SUBMITTED", "SAFETY", NOW.minusMinutes(2));
        insertReport(9_110_003L, "SUBMITTED", "SAFETY", NOW.minusMinutes(3));
        String cursor = service.list(ADMIN_A, "SUBMITTED", "SAFETY", null, null, null, 1)
                .pagination().nextCursor();
        assertInvalid(() -> service.list(ADMIN_A, "REVIEWING", "SAFETY", null, null, cursor, 1));
        String tampered = (cursor.startsWith("A") ? "B" : "A") + cursor.substring(1);
        assertInvalid(() -> service.list(ADMIN_A, "SUBMITTED", "SAFETY", null, null,
                tampered, 1));
        assertInvalid(() -> service.list(ADMIN_A, "SUBMITTED", "SAFETY", null, null, "broken", 1));
    }

    @Test
    void 잘못된_filter_날짜_범위와_size를_거절한다() {
        assertInvalid(() -> service.list(ADMIN_A, "UNKNOWN", null, null, null, null, 20));
        assertInvalid(() -> service.list(ADMIN_A, null, "UNKNOWN", null, null, null, 20));
        assertInvalid(() -> service.list(ADMIN_A, null, null, "bad", null, null, 20));
        assertInvalid(() -> service.list(ADMIN_A, null, null, NOW.toString(), NOW.toString(), null, 20));
        assertInvalid(() -> service.list(ADMIN_A, null, null, null, null, null, 101));
    }

    @Test
    void 상세은_최소_회원정보만_조회하고_암호화_원문을_노출하지_않는다() {
        long reportId = insertReport("SUBMITTED", "OTHER", NOW.minusMinutes(1));
        jdbc.update("UPDATE reports SET detail_encrypted=? WHERE id=?", new byte[]{1, 2, 3}, reportId);
        AdminReportDetailResponse detail = service.detail(ADMIN_A, reportId);
        assertThat(detail.reporter().memberId()).isEqualTo(REPORTER);
        assertThat(detail.reportedMember().memberId()).isEqualTo(REPORTED);
        assertThat(detail.reasonCode().name()).isEqualTo("OTHER");
    }

    @Test
    void 동일_RESOLVED_동시_요청은_모두_성공하고_감사로그가_하나다() throws Exception {
        assertSameTerminalRace(AdminReportTargetStatus.RESOLVED, "REPORT_RESOLVE");
    }

    @Test
    void 동일_REJECTED_동시_요청은_모두_성공하고_감사로그가_하나다() throws Exception {
        assertSameTerminalRace(AdminReportTargetStatus.REJECTED, "REPORT_REJECT");
    }

    @Test
    void RESOLVED와_REJECTED_경합은_단일_terminal과_감사로그만_남긴다() throws Exception {
        long reportId = insertReport("SUBMITTED", "SAFETY", NOW.minusMinutes(1));
        List<Result> results = race(reportId, AdminReportTargetStatus.RESOLVED, AdminReportTargetStatus.REJECTED);
        assertThat(results.stream().filter(Result::success)).hasSize(1);
        assertThat(results.stream().filter(result -> result.errorCode() == ErrorCode.ADMIN_REPORT_STATUS_CONFLICT))
                .hasSize(1);
        assertThat(actionCount(reportId)).isOne();
        assertThat(reportStatus(reportId)).isIn("RESOLVED", "REJECTED");
    }

    @Test
    void REVIEWING과_RESOLVED_경합의_최종상태는_RESOLVED이고_감사로그가_하나다() throws Exception {
        assertReviewTerminalRace(AdminReportTargetStatus.RESOLVED, "RESOLVED");
    }

    @Test
    void REVIEWING과_REJECTED_경합의_최종상태는_REJECTED이고_감사로그가_하나다() throws Exception {
        assertReviewTerminalRace(AdminReportTargetStatus.REJECTED, "REJECTED");
    }

    @Test
    void 동일_targetStatus_반복은_timestamp와_감사로그를_바꾸지_않는다() {
        long reportId = insertReport("SUBMITTED", "SAFETY", NOW.minusMinutes(1));
        AdminReportDetailResponse first = service.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED);
        AdminReportDetailResponse repeated = service.changeStatus(ADMIN_B, reportId, AdminReportTargetStatus.RESOLVED);
        assertThat(repeated.updatedAt()).isEqualTo(first.updatedAt());
        assertThat(repeated.resolvedAt()).isEqualTo(first.resolvedAt());
        assertThat(actionCount(reportId)).isOne();
    }

    @Test
    void terminal_처리_뒤_다른_targetStatus는_409다() {
        long reportId = insertReport("SUBMITTED", "SAFETY", NOW.minusMinutes(1));
        service.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.REJECTED);
        assertThatThrownBy(() -> service.changeStatus(ADMIN_B, reportId, AdminReportTargetStatus.RESOLVED))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_REPORT_STATUS_CONFLICT));
    }

    @Test
    void admin_action_insert_실패는_report_update도_rollback한다() {
        long reportId = insertReport("SUBMITTED", "SAFETY", NOW.minusMinutes(1));
        jdbc.execute("""
                CREATE FUNCTION fail_admin_action_insert() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced admin action failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_admin_action_insert BEFORE INSERT ON admin_actions
                FOR EACH ROW EXECUTE FUNCTION fail_admin_action_insert()
                """);
        try {
            assertThatThrownBy(() -> service.changeStatus(
                    ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED)).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_admin_action_insert ON admin_actions");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_admin_action_insert()");
        }
        assertThat(reportStatus(reportId)).isEqualTo("SUBMITTED");
        assertThat(actionCount(reportId)).isZero();
    }

    @Test
    void 처리_전후_매칭과_회원_점수_제재_이벤트는_변하지_않는다() {
        long reportId = insertReport("SUBMITTED", "SAFETY", NOW.minusMinutes(1));
        String before = sideEffects();
        service.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED);
        assertThat(sideEffects()).isEqualTo(before);
    }

    private void assertSameTerminalRace(AdminReportTargetStatus target, String actionType) throws Exception {
        long reportId = insertReport("SUBMITTED", "SAFETY", NOW.minusMinutes(1));
        List<Result> results = race(reportId, target, target);
        assertThat(results).allMatch(Result::success);
        assertThat(reportStatus(reportId)).isEqualTo(target.name());
        assertThat(actionCount(reportId)).isOne();
        assertThat(jdbc.queryForObject("SELECT action_type FROM admin_actions WHERE report_id=?",
                String.class, reportId)).isEqualTo(actionType);
    }

    private void assertReviewTerminalRace(AdminReportTargetStatus terminal, String expected) throws Exception {
        long reportId = insertReport("SUBMITTED", "SAFETY", NOW.minusMinutes(1));
        race(reportId, AdminReportTargetStatus.REVIEWING, terminal);
        if (reportStatus(reportId).equals("REVIEWING")) {
            service.changeStatus(ADMIN_B, reportId, terminal);
        }
        assertThat(reportStatus(reportId)).isEqualTo(expected);
        assertThat(actionCount(reportId)).isOne();
    }

    private List<Result> race(
            long reportId, AdminReportTargetStatus firstTarget, AdminReportTargetStatus secondTarget)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Result> first = executor.submit(() -> invoke(ADMIN_A, reportId, firstTarget, ready, start));
            Future<Result> second = executor.submit(() -> invoke(ADMIN_B, reportId, secondTarget, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Result invoke(long adminId, long reportId, AdminReportTargetStatus target,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            service.changeStatus(adminId, reportId, target);
            return new Result(true, null);
        } catch (BusinessException exception) {
            return new Result(false, exception.getErrorCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private long insertReport(String status, String reason, OffsetDateTime createdAt) {
        return insertReport(REPORTER, status, reason, createdAt);
    }

    private long insertReport(long reporter, String status, String reason, OffsetDateTime createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO reports(
                    reporter_member_id, reported_member_id, group_id, reason_code,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, reporter, REPORTED, GROUP, reason, status, createdAt, createdAt);
    }

    private String reportStatus(long reportId) {
        return jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId);
    }

    private int actionCount(long reportId) {
        return jdbc.queryForObject("SELECT count(*) FROM admin_actions WHERE report_id=?",
                Integer.class, reportId);
    }

    private String sideEffects() {
        return jdbc.queryForObject("""
                SELECT concat_ws('|',
                    (SELECT count(*) FROM match_penalty_events),
                    (SELECT count(*) FROM match_cooldowns),
                    (SELECT penalty_score FROM members WHERE id=?),
                    (SELECT manner_temperature FROM members WHERE id=?),
                    (SELECT status FROM members WHERE id=?),
                    (SELECT count(*) FROM match_events WHERE group_id=?),
                    (SELECT status FROM match_groups WHERE id=?),
                    (SELECT count(*) FROM match_pools),
                    (SELECT count(*) FROM match_proposals),
                    (SELECT count(*) FROM match_attempts))
                """, String.class, REPORTED, REPORTED, REPORTED, GROUP, GROUP);
    }

    private jakarta.servlet.http.Cookie cookie(long memberId) {
        return new jakarta.servlet.http.Cookie("access_token",
                jwtProvider.createAccessToken(memberId, "ACTIVE"));
    }

    private void assertInvalid(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_REPORT_INVALID_REQUEST));
    }

    private record Result(boolean success, ErrorCode errorCode) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedAdminReportClock() {
            return Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
