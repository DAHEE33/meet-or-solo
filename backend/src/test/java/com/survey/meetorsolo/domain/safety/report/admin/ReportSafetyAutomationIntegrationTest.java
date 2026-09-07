package com.survey.meetorsolo.domain.safety.report.admin;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberActionRequest;
import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberActionReasonCode;
import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberActionType;
import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberStatus;
import com.survey.meetorsolo.domain.admin.member.dto.AdminSuspensionDuration;
import com.survey.meetorsolo.domain.admin.member.service.AdminMemberService;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertPageResponse;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertResponse;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertStatus;
import com.survey.meetorsolo.domain.admin.safety.service.AdminSafetyAlertService;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.report.admin.dto.AdminReportTargetStatus;
import com.survey.meetorsolo.domain.safety.report.admin.service.AdminReportService;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 신고 누적·안전 자동화 통합 검증 (docs/19_ADMIN_MEMBER_SAFETY_ROADMAP.md 4.3).
 *
 * <p>확정 정책: 유효 신고 1건당 penalty score {@code +5}, 매너온도 {@code -5.00}(하한
 * {@code 20.00}), cooldown 미생성. 30일 window의 {@code (reporter, group)} distinct 누적이
 * 3건에 도달하면 관리자 알림만 생성하고 회원 status는 바꾸지 않는다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.jwt.secret=report-safety-automation-test-secret",
        "app.admin.report.cursor-hmac-secret=admin-report-cursor-test-secret-32-bytes",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        "app.admin.member.suspension-scheduler-enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(ReportSafetyAutomationIntegrationTest.FixedClockConfiguration.class)
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class ReportSafetyAutomationIntegrationTest {

    private static final long ADMIN_A = 9_110_010L;
    private static final long ADMIN_B = 9_110_011L;
    private static final long USER = 9_110_009L;
    private static final long REPORTED = 9_110_002L;
    private static final long REPORTER_1 = 9_110_001L;
    private static final long REPORTER_2 = 9_110_003L;
    private static final long REPORTER_3 = 9_110_004L;
    private static final long GROUP_1 = 9_170_001L;
    private static final long GROUP_2 = 9_170_002L;
    private static final long GROUP_3 = 9_170_003L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-15T12:00:00+09:00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired AdminReportService reports;
    @Autowired AdminMemberService adminMembers;
    @Autowired AdminSafetyAlertService alerts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @BeforeEach
    void prepare() {
        jdbc.update("UPDATE members SET role='ADMIN', nickname='admin-a' WHERE id=?", ADMIN_A);
        jdbc.update("UPDATE members SET role='ADMIN', nickname='admin-b' WHERE id=?", ADMIN_B);
        insertGroup(GROUP_1);
        insertGroup(GROUP_2);
        insertGroup(GROUP_3);
        insertGroupMembers(GROUP_1, REPORTED, REPORTER_1);
        insertGroupMembers(GROUP_2, REPORTED, REPORTER_2);
        insertGroupMembers(GROUP_3, REPORTED, REPORTER_3);
    }

    @Test
    void 유효_판정_1건은_penalty_5와_매너온도_5도_차감을_적용하고_cooldown을_만들지_않는다() {
        long reportId = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(1));

        reports.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED);

        assertThat(penaltyScore()).isEqualTo(5);
        assertThat(mannerTemperature()).isEqualByComparingTo("31.50");
        assertThat(cooldownCount()).isZero();
        assertThat(memberStatus()).isEqualTo("ACTIVE");

        assertThat(penaltyEventCount(reportId)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT concat_ws('|', event_type, score_delta, manner_temperature_delta)
                FROM match_penalty_events WHERE related_report_id=?
                """, String.class, reportId)).isEqualTo("REPORT_CONFIRMED|5|-5.00");
    }

    @Test
    void 기각은_penalty와_매너온도를_적용하지_않는다() {
        long reportId = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(1));

        reports.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.REJECTED);

        assertThat(penaltyScore()).isZero();
        assertThat(mannerTemperature()).isEqualByComparingTo("36.50");
        assertThat(penaltyEventCount(reportId)).isZero();
        assertThat(openAlertCount()).isZero();
    }

    @Test
    void 같은_신고를_다시_유효_판정해도_penalty와_매너온도는_한_번만_적용된다() {
        long reportId = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(1));

        reports.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED);
        reports.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED);
        reports.changeStatus(ADMIN_B, reportId, AdminReportTargetStatus.RESOLVED);

        assertThat(penaltyScore()).isEqualTo(5);
        assertThat(mannerTemperature()).isEqualByComparingTo("31.50");
        assertThat(penaltyEventCount(reportId)).isOne();
    }

    @Test
    void 두_관리자가_동시에_유효_판정해도_penalty_event는_하나다() throws Exception {
        long reportId = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(1));

        List<Boolean> results = race(
                () -> reports.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED),
                () -> reports.changeStatus(ADMIN_B, reportId, AdminReportTargetStatus.RESOLVED));

        assertThat(results).contains(true);
        assertThat(reportStatus(reportId)).isEqualTo("RESOLVED");
        assertThat(penaltyEventCount(reportId)).isOne();
        assertThat(penaltyScore()).isEqualTo(5);
        assertThat(mannerTemperature()).isEqualByComparingTo("31.50");
    }

    @Test
    void 같은_만남에서_사유만_다른_신고_3건은_누적_1건이므로_알림을_만들지_않는다() {
        // uq_reports_reporter_reported_group_reason이 사유별 row를 허용하므로 접수 자체는 3건이다.
        long first = insertReport(REPORTER_1, GROUP_1, "RUDE", NOW.minusDays(3));
        long second = insertReport(REPORTER_1, GROUP_1, "NO_SHOW", NOW.minusDays(2));
        long third = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(1));

        reports.changeStatus(ADMIN_A, first, AdminReportTargetStatus.RESOLVED);
        reports.changeStatus(ADMIN_A, second, AdminReportTargetStatus.RESOLVED);
        reports.changeStatus(ADMIN_A, third, AdminReportTargetStatus.RESOLVED);

        // penalty와 매너온도는 유효 판정 건마다 적용된다.
        assertThat(penaltyScore()).isEqualTo(15);
        assertThat(mannerTemperature()).isEqualByComparingTo("21.50");
        // 누적 집계는 (reporter, group) distinct이므로 1건이다.
        assertThat(recentValidReportCount()).isEqualTo(1);
        assertThat(openAlertCount()).isZero();
        assertThat(safetyReviewRequired()).isFalse();
    }

    @Test
    void 서로_다른_reporter와_group의_유효_신고_3건은_알림을_만들고_회원_status는_바꾸지_않는다() {
        long third = resolveThreeDistinctReports();

        assertThat(recentValidReportCount()).isEqualTo(3);
        assertThat(safetyReviewRequired()).isTrue();
        assertThat(openAlertCount()).isOne();
        assertThat(memberStatus()).isEqualTo("ACTIVE");

        AdminSafetyAlertResponse alert = alerts
                .list(ADMIN_A, AdminSafetyAlertStatus.OPEN.name(), null, 20).alerts().get(0);
        assertThat(alert.triggerReportId()).isEqualTo(third);
        assertThat(alert.validReportCount()).isEqualTo(3);
        assertThat(alert.reportedMemberId()).isEqualTo(REPORTED);
        assertThat(alert.status()).isEqualTo(AdminSafetyAlertStatus.OPEN);
        assertThat(alert.handledAt()).isNull();
    }

    @Test
    void 임계_돌파_뒤_추가_유효_판정은_미확인_알림이_있는_동안_새_알림을_만들지_않는다() {
        resolveThreeDistinctReports();
        assertThat(openAlertCount()).isOne();

        long fourth = insertReport(REPORTER_3, GROUP_2, "SAFETY", NOW.minusDays(1));
        reports.changeStatus(ADMIN_A, fourth, AdminReportTargetStatus.RESOLVED);

        assertThat(recentValidReportCount()).isEqualTo(4);
        assertThat(alertCount()).isOne();
        assertThat(penaltyEventCount(fourth)).isOne();
    }

    @Test
    void 매너온도는_하한_20도_아래로_내려가지_않는다() {
        BigDecimal[] expected = {
                new BigDecimal("31.50"), new BigDecimal("26.50"), new BigDecimal("21.50"),
                new BigDecimal("20.00"), new BigDecimal("20.00")};
        long[] reporters = {REPORTER_1, REPORTER_2, REPORTER_3, REPORTER_1, REPORTER_2};
        long[] groups = {GROUP_1, GROUP_1, GROUP_1, GROUP_2, GROUP_2};

        for (int i = 0; i < expected.length; i++) {
            long reportId = insertReport(reporters[i], groups[i], "SAFETY", NOW.minusDays(1));
            reports.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED);
            assertThat(mannerTemperature()).isEqualByComparingTo(expected[i]);
        }

        assertThat(penaltyScore()).isEqualTo(25);
        // 하한에 도달한 뒤에도 penalty event row는 남고 실제 차감량 0.00을 기록한다.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM match_penalty_events
                WHERE related_report_id IS NOT NULL AND manner_temperature_delta = 0.00
                """, Integer.class)).isOne();
    }

    @Test
    void window_30일을_벗어난_유효_신고는_누적에서_제외된다() {
        long old1 = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(31));
        long old2 = insertReport(REPORTER_2, GROUP_2, "SAFETY", NOW.minusDays(40));
        long recent = insertReport(REPORTER_3, GROUP_3, "SAFETY", NOW.minusDays(29));

        reports.changeStatus(ADMIN_A, old1, AdminReportTargetStatus.RESOLVED);
        reports.changeStatus(ADMIN_A, old2, AdminReportTargetStatus.RESOLVED);
        reports.changeStatus(ADMIN_A, recent, AdminReportTargetStatus.RESOLVED);

        // penalty는 판정 건마다 적용되지만 누적 집계는 window 안의 1건뿐이다.
        assertThat(penaltyScore()).isEqualTo(15);
        assertThat(recentValidReportCount()).isEqualTo(1);
        assertThat(openAlertCount()).isZero();
    }

    @Test
    void ACTION_TAKEN도_유효_신고로_집계된다() {
        long reportId = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(1));
        reports.changeStatus(ADMIN_A, reportId, AdminReportTargetStatus.RESOLVED);

        adminMembers.act(ADMIN_A, REPORTED, UUID.randomUUID().toString(), new AdminMemberActionRequest(
                AdminMemberActionType.WARNING, AdminMemberActionReasonCode.COMMUNITY_GUIDELINE,
                null, null, reportId, AdminMemberStatus.ACTIVE));

        assertThat(reportStatus(reportId)).isEqualTo("ACTION_TAKEN");
        assertThat(recentValidReportCount()).isEqualTo(1);
        // 제재 연결만으로 penalty가 다시 적용되지 않는다.
        assertThat(penaltyScore()).isEqualTo(5);
        assertThat(penaltyEventCount(reportId)).isOne();
    }

    @Test
    void 관리자_확인은_멱등하고_상태와_처리자를_기록한다() {
        resolveThreeDistinctReports();
        long alertId = alerts.list(ADMIN_A, null, null, 20).alerts().get(0).alertId();

        AdminSafetyAlertResponse first = alerts.acknowledge(ADMIN_A, alertId);
        AdminSafetyAlertResponse again = alerts.acknowledge(ADMIN_B, alertId);

        assertThat(first.status()).isEqualTo(AdminSafetyAlertStatus.ACKNOWLEDGED);
        assertThat(first.handledAt()).isNotNull();
        assertThat(again.status()).isEqualTo(AdminSafetyAlertStatus.ACKNOWLEDGED);
        assertThat(again.handledAt()).isEqualTo(first.handledAt());
        assertThat(handledAdminId(alertId)).isEqualTo(ADMIN_A);
        assertThat(openAlertCount()).isZero();
    }

    @Test
    void 회원_제재는_같은_transaction에서_미종료_알림을_종료한다() {
        resolveThreeDistinctReports();
        assertThat(openAlertCount()).isOne();
        clearActiveMatching();

        adminMembers.act(ADMIN_A, REPORTED, UUID.randomUUID().toString(), new AdminMemberActionRequest(
                AdminMemberActionType.SUSPEND, AdminMemberActionReasonCode.HARASSMENT,
                null, AdminSuspensionDuration.THREE_DAYS, null, AdminMemberStatus.ACTIVE));

        assertThat(memberStatus()).isEqualTo("SUSPENDED");
        assertThat(alertStatus()).isEqualTo("CLOSED");
        assertThat(handledAdminIdOfOnlyAlert()).isEqualTo(ADMIN_A);
        assertThat(openAlertCount()).isZero();
    }

    @Test
    void 경고는_알림을_종료하지_않는다() {
        resolveThreeDistinctReports();

        adminMembers.act(ADMIN_A, REPORTED, UUID.randomUUID().toString(), new AdminMemberActionRequest(
                AdminMemberActionType.WARNING, AdminMemberActionReasonCode.COMMUNITY_GUIDELINE,
                null, null, null, AdminMemberStatus.ACTIVE));

        assertThat(openAlertCount()).isOne();
    }

    @Test
    void 알림_목록은_status_filter와_cursor_pagination에서_누락과_중복을_만들지_않는다() {
        resolveThreeDistinctReports();
        long acknowledged = alerts.list(ADMIN_A, null, null, 20).alerts().get(0).alertId();
        alerts.acknowledge(ADMIN_A, acknowledged);
        // 확인 처리로 OPEN이 사라졌으므로 새 누적으로 두 번째 알림을 만든다.
        long extra = insertReport(REPORTER_3, GROUP_2, "SAFETY", NOW.minusDays(1));
        reports.changeStatus(ADMIN_A, extra, AdminReportTargetStatus.RESOLVED);
        assertThat(alertCount()).isEqualTo(2);

        AdminSafetyAlertPageResponse first = alerts.list(ADMIN_A, null, null, 1);
        AdminSafetyAlertPageResponse second = alerts.list(
                ADMIN_A, null, first.pagination().nextCursor(), 1);

        assertThat(first.pagination().hasNext()).isTrue();
        assertThat(second.pagination().hasNext()).isFalse();
        List<Long> ids = List.of(
                first.alerts().get(0).alertId(), second.alerts().get(0).alertId());
        assertThat(ids).doesNotHaveDuplicates().hasSize(2);
        assertThat(first.openCount()).isOne();

        assertThat(alerts.list(ADMIN_A, AdminSafetyAlertStatus.ACKNOWLEDGED.name(), null, 20)
                .alerts()).extracting(AdminSafetyAlertResponse::alertId)
                .containsExactly(acknowledged);
        assertThat(alerts.list(ADMIN_A, AdminSafetyAlertStatus.CLOSED.name(), null, 20)
                .alerts()).isEmpty();
    }

    /**
     * 신고 접수부터 제재까지 전부 실제 HTTP endpoint로 통과하는지 확인한다.
     *
     * <p>다른 테스트는 신고 row를 직접 넣어 4.3 로직에 집중하지만, 이 테스트는 SQL INSERT 없이
     * 실제 사용자·관리자 경로만 사용한다. 신고 접수의 참여자·기간 검증까지 함께 태운다.
     */
    @Test
    void 신고_접수부터_판정_알림_확인_제재까지_실제_HTTP_경로로_동작한다() throws Exception {
        long first = submitReportViaApi(REPORTER_1, GROUP_1, "RUDE");
        long second = submitReportViaApi(REPORTER_2, GROUP_2, "NO_SHOW");
        long third = submitReportViaApi(REPORTER_3, GROUP_3, "SAFETY");

        // 접수만으로는 penalty, 매너온도, 알림이 생기지 않는다.
        assertThat(penaltyScore()).isZero();
        assertThat(mannerTemperature()).isEqualByComparingTo("36.50");
        assertThat(alertCount()).isZero();

        resolveViaApi(first);
        assertThat(penaltyScore()).isEqualTo(5);
        assertThat(mannerTemperature()).isEqualByComparingTo("31.50");
        assertThat(openAlertCount()).isZero();

        resolveViaApi(second);
        assertThat(penaltyScore()).isEqualTo(10);
        assertThat(mannerTemperature()).isEqualByComparingTo("26.50");
        assertThat(openAlertCount()).isZero();

        resolveViaApi(third);
        assertThat(penaltyScore()).isEqualTo(15);
        assertThat(mannerTemperature()).isEqualByComparingTo("21.50");
        assertThat(memberStatus()).isEqualTo("ACTIVE");
        assertThat(cooldownCount()).isZero();

        String listJson = mockMvc.perform(get("/api/admin/safety-alerts")
                        .param("status", "OPEN").cookie(cookie(ADMIN_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openCount").value(1))
                .andExpect(jsonPath("$.data.alerts[0].validReportCount").value(3))
                .andExpect(jsonPath("$.data.alerts[0].triggerReportId").value(third))
                .andExpect(jsonPath("$.data.alerts[0].status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        long alertId = jsonLong(listJson, "alertId");

        mockMvc.perform(put("/api/admin/safety-alerts/{id}/acknowledgement", alertId)
                        .cookie(cookie(ADMIN_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));
        assertThat(openAlertCount()).isZero();

        // 회원 상세에서 누적 건수와 제한 검토 대상 표시를 확인한다.
        mockMvc.perform(get("/api/admin/members/{id}", REPORTED).cookie(cookie(ADMIN_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentValidReportCount").value(3))
                .andExpect(jsonPath("$.data.safetyReviewRequired").value(true))
                .andExpect(jsonPath("$.data.penaltyScore").value(15))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        clearActiveMatching();
        mockMvc.perform(post("/api/admin/members/{id}/actions", REPORTED)
                        .cookie(cookie(ADMIN_A))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"SUSPEND","reasonCode":"HARASSMENT",
                                 "suspensionDuration":"THREE_DAYS","expectedStatus":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        assertThat(alertStatus()).isEqualTo("CLOSED");
    }

    @Test
    void 접수_API는_참여하지_않은_group과_기간이_지난_group을_거절한다() throws Exception {
        // 참여자가 아닌 회원은 group 존재 자체를 알 수 없어야 한다.
        mockMvc.perform(post("/api/match-groups/{groupId}/reports", GROUP_1)
                        .cookie(cookie(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedMemberId\":" + REPORTED + ",\"reasonCode\":\"RUDE\"}"))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE match_groups SET completed_at=? WHERE id=?",
                NOW.minusDays(31), GROUP_1);
        mockMvc.perform(post("/api/match-groups/{groupId}/reports", GROUP_1)
                        .cookie(cookie(REPORTER_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedMemberId\":" + REPORTED + ",\"reasonCode\":\"RUDE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_WINDOW_EXPIRED"));

        assertThat(penaltyScore()).isZero();
        assertThat(alertCount()).isZero();
    }

    @Test
    void 안전_알림_API는_ADMIN만_허용하고_일반_회원과_미인증을_구분해_거절한다() throws Exception {
        resolveThreeDistinctReports();
        long alertId = alerts.list(ADMIN_A, null, null, 20).alerts().get(0).alertId();

        mockMvc.perform(get("/api/admin/safety-alerts").cookie(cookie(ADMIN_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openCount").value(1))
                .andExpect(jsonPath("$.data.alerts[0].validReportCount").value(3));

        mockMvc.perform(get("/api/admin/safety-alerts").cookie(cookie(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/safety-alerts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(put("/api/admin/safety-alerts/{id}/acknowledgement", alertId)
                        .cookie(cookie(USER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/safety-alerts/{id}/acknowledgement", alertId))
                .andExpect(status().isUnauthorized());
        // 권한 없는 요청이 상태를 바꾸지 않았는지 확인한다.
        assertThat(openAlertCount()).isOne();

        mockMvc.perform(put("/api/admin/safety-alerts/{id}/acknowledgement", 999_999L)
                        .cookie(cookie(ADMIN_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_SAFETY_ALERT_NOT_FOUND"));
    }

    @Test
    void 잘못된_status와_size_cursor는_400으로_거절한다() throws Exception {
        mockMvc.perform(get("/api/admin/safety-alerts").param("status", "UNKNOWN")
                        .cookie(cookie(ADMIN_A)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ADMIN_SAFETY_ALERT_INVALID_REQUEST"));
        mockMvc.perform(get("/api/admin/safety-alerts").param("size", "0")
                        .cookie(cookie(ADMIN_A)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/safety-alerts").param("size", "101")
                        .cookie(cookie(ADMIN_A)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/safety-alerts").param("cursor", "broken")
                        .cookie(cookie(ADMIN_A)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 다른_filter로_만든_cursor는_재사용할_수_없다() {
        resolveThreeDistinctReports();
        String cursor = alerts.list(ADMIN_A, AdminSafetyAlertStatus.OPEN.name(), null, 1)
                .pagination().nextCursor();
        // OPEN 알림이 1건뿐이라 nextCursor가 없으면 검증할 것이 없다.
        if (cursor == null) {
            return;
        }
        assertThatThrownBy(() -> alerts.list(ADMIN_A, null, cursor, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 신고_유효_판정과_회원_제재가_동시에_실행돼도_deadlock_없이_한_방향으로_끝난다() throws Exception {
        // 두 service의 잠금 순서가 member -> report로 통일되어 있는지 확인하는 회귀 테스트다.
        long resolved = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(2));
        reports.changeStatus(ADMIN_A, resolved, AdminReportTargetStatus.RESOLVED);
        long pending = insertReport(REPORTER_2, GROUP_2, "SAFETY", NOW.minusDays(1));
        // active 매칭이 남아 있으면 제재가 409로 거절되어 race가 성립하지 않는다.
        clearActiveMatching();

        List<Boolean> results = race(
                () -> reports.changeStatus(ADMIN_A, pending, AdminReportTargetStatus.RESOLVED),
                () -> adminMembers.act(ADMIN_B, REPORTED, UUID.randomUUID().toString(),
                        new AdminMemberActionRequest(
                                AdminMemberActionType.SUSPEND, AdminMemberActionReasonCode.HARASSMENT,
                                null, AdminSuspensionDuration.THREE_DAYS, null,
                                AdminMemberStatus.ACTIVE)));

        assertThat(results).contains(true);
        assertThat(deadlockOccurred).isFalse();
        assertThat(memberStatus()).isIn("ACTIVE", "SUSPENDED");
    }

    private volatile boolean deadlockOccurred = false;

    private long resolveThreeDistinctReports() {
        long first = insertReport(REPORTER_1, GROUP_1, "SAFETY", NOW.minusDays(3));
        long second = insertReport(REPORTER_2, GROUP_2, "RUDE", NOW.minusDays(2));
        long third = insertReport(REPORTER_3, GROUP_3, "NO_SHOW", NOW.minusDays(1));
        reports.changeStatus(ADMIN_A, first, AdminReportTargetStatus.RESOLVED);
        reports.changeStatus(ADMIN_A, second, AdminReportTargetStatus.RESOLVED);
        reports.changeStatus(ADMIN_A, third, AdminReportTargetStatus.RESOLVED);
        return third;
    }

    private List<Boolean> race(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = List.of(
                    pool.submit(() -> attempt(start, first)),
                    pool.submit(() -> attempt(start, second)));
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean attempt(CountDownLatch start, Runnable action) throws InterruptedException {
        start.await();
        try {
            action.run();
            return true;
        } catch (RuntimeException exception) {
            if (rootMessage(exception).contains("deadlock detected")) {
                deadlockOccurred = true;
            }
            return false;
        }
    }

    private static String rootMessage(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            messages.append(String.valueOf(current.getMessage())).append('\n');
        }
        return messages.toString();
    }

    /**
     * fixture의 피신고 회원에게는 WAITING pool과 SENT proposal이 남아 있어
     * {@code AdminMemberService.act()}가 {@code ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT}로 거절한다.
     * 제재 흐름을 검증하려면 먼저 active 매칭을 정리한다.
     */
    private void clearActiveMatching() {
        jdbc.update("UPDATE match_pools SET status='CANCELLED' WHERE member_id=?", REPORTED);
        jdbc.update("UPDATE match_proposals SET status='EXPIRED' WHERE member_id=? AND status='SENT'",
                REPORTED);
        jdbc.update("UPDATE match_group_members SET status='LEFT' WHERE member_id=?", REPORTED);
    }

    /**
     * 신고 접수 API는 group 참여자만 허용한다({@code existsParticipant}).
     * 활성 member 부분 unique index를 피하려고 종료 상태인 {@code COMPLETED}를 사용한다.
     */
    private void insertGroupMembers(long groupId, long... memberIds) {
        for (long memberId : memberIds) {
            jdbc.update("""
                    INSERT INTO match_group_members(
                        group_id, member_id, status, allow_minimum_two, created_at, updated_at
                    ) VALUES (?, ?, 'COMPLETED', TRUE, ?, ?)
                    ON CONFLICT (group_id, member_id) DO NOTHING
                    """, groupId, memberId, NOW.minusDays(2), NOW.minusDays(2));
        }
    }

    /** {@code uq_match_groups_attempt} 때문에 group마다 별도 attempt가 필요하다. */
    private void insertGroup(long groupId) {
        long attemptId = groupId + 1_000_000L;
        // 신고 접수 API의 30일 window 안에 두어야 실제 HTTP 경로 검증이 가능하다.
        OffsetDateTime at = NOW.minusDays(2);
        jdbc.update("""
                INSERT INTO match_attempts(
                    id, festival_id, target_group_size, status, score, created_by,
                    started_at, expires_at, created_at, updated_at
                ) VALUES (?, 9100001, 2, 'CONFIRMED', 80.00, 'SCHEDULER', ?, ?, ?, ?)
                """, attemptId, at, at.plusMinutes(3), at, at);
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count, confirmed_at,
                    completed_at, created_at, updated_at
                ) VALUES (?, ?, 9100001, 'COMPLETED', 2, ?, ?, ?, ?)
                """, groupId, attemptId, at, at, at, at);
    }

    private long insertReport(long reporter, long groupId, String reason, OffsetDateTime createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO reports(
                    reporter_member_id, reported_member_id, group_id, reason_code,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'SUBMITTED', ?, ?) RETURNING id
                """, Long.class, reporter, REPORTED, groupId, reason, createdAt, createdAt);
    }

    private String reportStatus(long reportId) {
        return jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId);
    }

    private int penaltyScore() {
        return jdbc.queryForObject(
                "SELECT penalty_score FROM members WHERE id=?", Integer.class, REPORTED);
    }

    private BigDecimal mannerTemperature() {
        return jdbc.queryForObject(
                "SELECT manner_temperature FROM members WHERE id=?", BigDecimal.class, REPORTED);
    }

    private String memberStatus() {
        return jdbc.queryForObject("SELECT status FROM members WHERE id=?", String.class, REPORTED);
    }

    private int cooldownCount() {
        return jdbc.queryForObject("SELECT count(*) FROM match_cooldowns WHERE member_id=?",
                Integer.class, REPORTED);
    }

    private int penaltyEventCount(long reportId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM match_penalty_events WHERE related_report_id=?",
                Integer.class, reportId);
    }

    private int alertCount() {
        return jdbc.queryForObject("SELECT count(*) FROM admin_safety_alerts", Integer.class);
    }

    private int openAlertCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM admin_safety_alerts WHERE status='OPEN'", Integer.class);
    }

    private String alertStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM admin_safety_alerts WHERE reported_member_id=?",
                String.class, REPORTED);
    }

    private long handledAdminId(long alertId) {
        return jdbc.queryForObject(
                "SELECT handled_admin_id FROM admin_safety_alerts WHERE id=?", Long.class, alertId);
    }

    private long handledAdminIdOfOnlyAlert() {
        return jdbc.queryForObject(
                "SELECT handled_admin_id FROM admin_safety_alerts WHERE reported_member_id=?",
                Long.class, REPORTED);
    }

    /** 실제 신고 접수 endpoint로 신고를 만들고 report ID를 돌려준다. */
    private long submitReportViaApi(long reporter, long groupId, String reasonCode)
            throws Exception {
        String body = mockMvc.perform(post("/api/match-groups/{groupId}/reports", groupId)
                        .cookie(cookie(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedMemberId\":" + REPORTED
                                + ",\"reasonCode\":\"" + reasonCode + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        return jsonLong(body, "reportId");
    }

    private void resolveViaApi(long reportId) throws Exception {
        mockMvc.perform(patch("/api/admin/reports/{id}/status", reportId)
                        .cookie(cookie(ADMIN_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    private static long jsonLong(String json, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        assertThat(matcher.find()).as("%s 필드를 찾을 수 없습니다: %s", field, json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private jakarta.servlet.http.Cookie cookie(long memberId) {
        return new jakarta.servlet.http.Cookie("access_token",
                jwtProvider.createAccessToken(memberId, "ACTIVE"));
    }

    private long recentValidReportCount() {
        return adminMembers.detail(ADMIN_A, REPORTED).recentValidReportCount();
    }

    private boolean safetyReviewRequired() {
        return adminMembers.detail(ADMIN_A, REPORTED).safetyReviewRequired();
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedReportSafetyClock() {
            return Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
