package com.survey.meetorsolo.domain.admin.member;

import static org.assertj.core.api.Assertions.*;

import com.survey.meetorsolo.domain.admin.member.dto.*;
import com.survey.meetorsolo.domain.admin.member.service.AdminMemberService;
import com.survey.meetorsolo.domain.admin.member.service.MemberSuspensionExpiryService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.jwt.secret=admin-member-integration-test-secret",
        "app.admin.report.cursor-hmac-secret=admin-member-cursor-test-secret-32-bytes",
        "app.admin.member.suspension-scheduler-enabled=false",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
@Import(AdminMemberIntegrationTest.FixedClockConfiguration.class)
class AdminMemberIntegrationTest {

    private static final long ADMIN = 9_810_001L;
    private static final long USER = 9_810_002L;
    private static final long PROFILE_USER = 9_810_003L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-16T12:00:00+09:00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired AdminMemberService service;
    @Autowired MemberSuspensionExpiryService expiryService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        jdbc.update("TRUNCATE TABLE members RESTART IDENTITY CASCADE");
        insertMember(ADMIN, "admin", "ADMIN", "ACTIVE");
        insertMember(USER, "member", "USER", "ACTIVE");
        insertMember(PROFILE_USER, "profile", "USER", "PROFILE_REQUIRED");
    }

    @Test
    void 목록은_검색_filter와_동일시각_ID_cursor를_안정적으로_처리한다() {
        var first = service.list(ADMIN, "memb", "ACTIVE", "USER", null, 1);
        assertThat(first.items()).extracting(AdminMemberListItemResponse::memberId).containsExactly(USER);
        assertThat(first.pagination().hasNext()).isFalse();
        assertThatThrownBy(() -> service.list(ADMIN, null, "UNKNOWN", null, null, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_MEMBER_INVALID_REQUEST));
    }

    @Test
    void WARNING은_감사로그만_남기고_동일_key는_멱등하다() {
        String key = UUID.randomUUID().toString();
        AdminMemberActionRequest request = request(AdminMemberActionType.WARNING, AdminMemberStatus.ACTIVE);
        service.act(ADMIN, USER, key, request);
        service.act(ADMIN, USER, key, request);
        assertThat(status(USER)).isEqualTo("ACTIVE");
        assertThat(actionCount(USER)).isOne();
        assertThat(jdbc.queryForObject("SELECT penalty_score FROM members WHERE id=?", Integer.class, USER)).isZero();
    }

    @Test
    void 같은_key를_다른_요청에_사용하면_충돌한다() {
        String key = UUID.randomUUID().toString();
        service.act(ADMIN, USER, key, request(AdminMemberActionType.WARNING, AdminMemberStatus.ACTIVE));
        assertThatThrownBy(() -> service.act(ADMIN, PROFILE_USER, key,
                request(AdminMemberActionType.WARNING, AdminMemberStatus.PROFILE_REQUIRED)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_ACTION_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void SUSPEND는_기간과_이전상태를_저장하고_lazy와_batch가_복구한다() {
        service.act(ADMIN, USER, UUID.randomUUID().toString(), new AdminMemberActionRequest(
                AdminMemberActionType.SUSPEND, AdminMemberActionReasonCode.SAFETY_RISK,
                null, AdminSuspensionDuration.ONE_DAY, null, AdminMemberStatus.ACTIVE));
        assertThat(status(USER)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT status_before_sanction FROM members WHERE id=?", String.class, USER))
                .isEqualTo("ACTIVE");
        jdbc.update("UPDATE members SET suspended_at=?, suspended_until=? WHERE id=?",
                NOW.minusDays(1), NOW.minusSeconds(1), USER);
        assertThat(expiryService.restoreBatch(100)).isOne();
        assertThat(status(USER)).isEqualTo("ACTIVE");
    }

    @Test
    void BAN과_UNBAN은_제재전_PROFILE_REQUIRED를_복원한다() {
        service.act(ADMIN, PROFILE_USER, UUID.randomUUID().toString(),
                request(AdminMemberActionType.BAN, AdminMemberStatus.PROFILE_REQUIRED));
        assertThat(status(PROFILE_USER)).isEqualTo("BANNED");
        service.act(ADMIN, PROFILE_USER, UUID.randomUUID().toString(),
                request(AdminMemberActionType.UNBAN, AdminMemberStatus.BANNED));
        assertThat(status(PROFILE_USER)).isEqualTo("PROFILE_REQUIRED");
        assertThat(actionCount(PROFILE_USER)).isEqualTo(2);
    }

    @Test
    void RESOLVED_신고_연결은_ACTION_TAKEN과_회원변경과_감사로그를_원자저장한다() {
        long reportId = insertReport("RESOLVED");
        AdminMemberActionRequest request = new AdminMemberActionRequest(
                AdminMemberActionType.BAN, AdminMemberActionReasonCode.SAFETY_RISK,
                "관리자 확인", null, reportId, AdminMemberStatus.ACTIVE);
        service.act(ADMIN, USER, UUID.randomUUID().toString(), request);
        assertThat(status(USER)).isEqualTo("BANNED");
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId))
                .isEqualTo("ACTION_TAKEN");
        assertThat(jdbc.queryForObject("SELECT report_id FROM admin_actions WHERE target_member_id=?", Long.class, USER))
                .isEqualTo(reportId);
    }

    @Test
    void active_pool_회원의_SUSPEND와_BAN은_409정책으로_거절한다() {
        insertActivePool();
        assertThatThrownBy(() -> service.act(ADMIN, USER, UUID.randomUUID().toString(),
                request(AdminMemberActionType.BAN, AdminMemberStatus.ACTIVE)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT));
        assertThat(status(USER)).isEqualTo("ACTIVE");
        assertThat(actionCount(USER)).isZero();
    }

    private AdminMemberActionRequest request(AdminMemberActionType action, AdminMemberStatus expected) {
        return new AdminMemberActionRequest(action, AdminMemberActionReasonCode.COMMUNITY_GUIDELINE,
                null, action == AdminMemberActionType.SUSPEND ? AdminSuspensionDuration.SEVEN_DAYS : null,
                null, expected);
    }

    private void insertMember(long id, String nickname, String role, String status) {
        jdbc.update("""
                INSERT INTO members(id, provider, provider_user_id, nickname, role, status, created_at, updated_at)
                VALUES (?, 'KAKAO', ?, ?, ?, ?, ?, ?)
                """, id, "admin-member-" + id, nickname, role, status, NOW.minusDays(1), NOW.minusDays(1));
    }

    private long insertReport(String status) {
        return jdbc.queryForObject("""
                INSERT INTO reports(reporter_member_id, reported_member_id, reason_code, status,
                    created_at, updated_at, resolved_at)
                VALUES (?, ?, 'SAFETY', ?, ?, ?, ?) RETURNING id
                """, Long.class, PROFILE_USER, USER, status, NOW.minusHours(1), NOW.minusHours(1), NOW.minusMinutes(30));
    }

    private void insertActivePool() {
        jdbc.update("""
                INSERT INTO festivals(id, content_id, content_type_id, title, status, created_at, updated_at)
                VALUES (9811001, 'admin-member-festival', '15', '테스트 축제', 'ACTIVE', ?, ?)
                """, NOW, NOW);
        jdbc.update("""
                INSERT INTO festival_checkins(id, member_id, festival_id, distance_meters, status,
                    checked_in_at, expires_at, created_at, updated_at)
                VALUES (9812001, ?, 9811001, 1, 'ACTIVE', ?, ?, ?, ?)
                """, USER, NOW.minusMinutes(1), NOW.plusHours(1), NOW, NOW);
        jdbc.update("""
                INSERT INTO match_pools(id, member_id, festival_id, checkin_id, preferred_group_size,
                    allow_minimum_two, tags, status, entered_at, search_expires_at, created_at, updated_at)
                VALUES (9813001, ?, 9811001, 9812001, 2, false, '[]', 'WAITING', ?, ?, ?, ?)
                """, USER, NOW, NOW.plusMinutes(1), NOW, NOW);
    }

    private long actionCount(long memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM admin_actions WHERE target_member_id=?", Long.class, memberId);
    }

    private String status(long memberId) {
        return jdbc.queryForObject("SELECT status FROM members WHERE id=?", String.class, memberId);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock clock() {
            return Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
