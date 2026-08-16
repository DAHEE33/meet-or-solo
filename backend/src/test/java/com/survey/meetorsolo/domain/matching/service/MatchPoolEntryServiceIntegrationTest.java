package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
@Import(MatchPoolEntryServiceIntegrationTest.FixedClockConfiguration.class)
class MatchPoolEntryServiceIntegrationTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-07-23T15:00:00+09:00");
    private static final long FESTIVAL_ID = 9_300_001L;
    private static final long MEMBER_ID = 9_310_001L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MatchPoolEntryService entries;

    @Autowired
    private MatchingQueryService queries;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("""
                INSERT INTO festivals(
                    id, content_id, content_type_id, title, status, created_at, updated_at
                ) VALUES (?, 'matching-rest-festival', '15', 'REST 매칭 테스트 축제', 'ACTIVE', ?, ?)
                """, FESTIVAL_ID, NOW.minusDays(1), NOW.minusDays(1));
        jdbc.update("""
                INSERT INTO festival_meeting_points(
                    festival_id, kakao_place_id, name, address, map_x, map_y, status,
                    assignment_order, created_at, updated_at
                ) VALUES (?, 'pool-entry-place', '신청 테스트 장소', '강원 테스트로 1',
                          128.1, 37.1, 'ACTIVE', 1, ?, ?)
                """, FESTIVAL_ID, NOW.minusDays(1), NOW.minusDays(1));
        for (long memberId = MEMBER_ID; memberId <= MEMBER_ID + 3; memberId++) {
            jdbc.update("""
                    INSERT INTO members(
                        id, provider, provider_user_id, nickname, role, status,
                        penalty_score, created_at, updated_at
                    ) VALUES (?, 'KAKAO', ?, ?, 'USER', 'ACTIVE', 0, ?, ?)
                    """, memberId, "matching-rest-" + memberId, "rest" + memberId,
                    NOW.minusDays(1), NOW.minusDays(1));
        }
        insertCheckin(MEMBER_ID, NOW.plusHours(1), "ACTIVE");
        insertCheckin(MEMBER_ID + 2, NOW.plusHours(1), "ACTIVE");
        insertCheckin(MEMBER_ID + 3, NOW.minusSeconds(1), "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 유효한_체크인으로_60초_WAITING_pool을_생성하고_현재_pool을_조회한다() {
        var created = entries.enter(MEMBER_ID, request());

        assertThat(created.status()).isEqualTo("WAITING");
        assertThat(created.tags()).isEmpty();
        assertThat(created.enteredAt()).isEqualTo(NOW);
        assertThat(created.searchExpiresAt()).isEqualTo(NOW.plusSeconds(60));

        var current = queries.currentPool(MEMBER_ID);
        assertThat(current.poolId()).isEqualTo(created.poolId());
        assertThat(current.festivalId()).isEqualTo(created.festivalId());
        assertThat(current.preferredGroupSize()).isEqualTo(created.preferredGroupSize());
        assertThat(current.allowMinimumTwo()).isEqualTo(created.allowMinimumTwo());
        assertThat(current.tags()).isEqualTo(created.tags());
        assertThat(current.status()).isEqualTo(created.status());
        assertThat(current.enteredAt().toInstant()).isEqualTo(created.enteredAt().toInstant());
        assertThat(current.searchExpiresAt().toInstant()).isEqualTo(created.searchExpiresAt().toInstant());
        assertThat(Duration.between(current.enteredAt(), current.searchExpiresAt()))
                .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void 유효한_체크인이_없거나_만료됐으면_400이다() {
        assertError(MEMBER_ID + 1, ErrorCode.MATCHING_INVALID_REQUEST);
        assertError(MEMBER_ID + 3, ErrorCode.MATCHING_INVALID_REQUEST);
    }

    @Test
    void 활성_만남_장소가_없으면_해당_축제_pool_진입만_차단한다() {
        jdbc.update("UPDATE festival_meeting_points SET status='INACTIVE' WHERE festival_id=?", FESTIVAL_ID);
        assertError(MEMBER_ID, ErrorCode.MATCHING_MEETING_POINT_NOT_READY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE member_id=?",
                Integer.class, MEMBER_ID)).isZero();
    }

    @Test
    void cooldown_중이면_409이고_penalty와_cooldown을_조회한다() {
        jdbc.update("UPDATE members SET penalty_score = 2 WHERE id = ?", MEMBER_ID);
        jdbc.update("""
                INSERT INTO match_cooldowns(
                    member_id, reason, status, starts_at, expires_at, created_at
                ) VALUES (?, 'TIMEOUT', 'ACTIVE', ?, ?, ?)
                """, MEMBER_ID, NOW.minusSeconds(10), NOW.plusMinutes(2), NOW.minusSeconds(10));

        assertError(MEMBER_ID, ErrorCode.MATCHING_CONFLICT);
        var restrictions = queries.restrictions(MEMBER_ID);
        assertThat(restrictions.penaltyScore()).isEqualTo(2);
        assertThat(restrictions.cooldown().active()).isTrue();
        assertThat(restrictions.cooldown().reason()).isEqualTo("TIMEOUT");
        assertThat(restrictions.cooldown().remainingSeconds()).isEqualTo(120);
        assertThat(restrictions.completionLock().active()).isFalse();
    }

    @Test
    void 정상_완료_후_confirmedAt_1시간_전에는_restriction과_pool_신청을_차단한다() {
        insertTerminalGroup(MEMBER_ID, "COMPLETED", "COMPLETED",
                NOW.minusMinutes(40), NOW.minusMinutes(1));

        var restrictions = queries.restrictions(MEMBER_ID);

        assertThat(restrictions.completionLock().active()).isTrue();
        assertThat(restrictions.completionLock().reason()).isEqualTo("MATCH_VALIDITY");
        assertThat(restrictions.completionLock().startsAt()).isEqualTo(NOW.minusMinutes(40));
        assertThat(restrictions.completionLock().expiresAt()).isEqualTo(NOW.plusMinutes(20));
        assertThat(restrictions.completionLock().remainingSeconds()).isEqualTo(1_200);
        assertError(MEMBER_ID, ErrorCode.MATCHING_COMPLETION_LOCKED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_cooldowns WHERE member_id=?",
                Integer.class, MEMBER_ID)).isZero();
    }

    @Test
    void 정상_완료_제한_경계_후에는_유효한_체크인으로_신청할_수_있다() {
        insertTerminalGroup(MEMBER_ID, "COMPLETED", "COMPLETED",
                NOW.minusHours(1), NOW.minusMinutes(5));

        assertThat(queries.restrictions(MEMBER_ID).completionLock().active()).isFalse();
        assertThat(entries.enter(MEMBER_ID, request()).status()).isEqualTo("WAITING");
    }

    @Test
    void 완료_제한이_끝나도_체크인이_만료됐으면_기존_체크인_오류를_유지한다() {
        insertTerminalGroup(MEMBER_ID + 3, "COMPLETED", "COMPLETED",
                NOW.minusHours(1), NOW.minusMinutes(5));

        assertError(MEMBER_ID + 3, ErrorCode.MATCHING_INVALID_REQUEST);
    }

    @Test
    void cancelled_group과_noShow_cancelled_left_member는_정상_완료_제한에서_제외한다() {
        insertTerminalGroup(MEMBER_ID, "CANCELLED", "CANCELLED",
                NOW.minusMinutes(10), null);
        assertThat(queries.restrictions(MEMBER_ID).completionLock().groupId()).isNull();

        for (String memberStatus : List.of("NO_SHOW", "CANCELLED", "LEFT")) {
            long memberId = MEMBER_ID + 1;
            cleanupMatchingHistory(memberId);
            insertTerminalGroup(memberId, "COMPLETED", memberStatus,
                    NOW.minusMinutes(10), NOW.minusMinutes(1));
            assertThat(queries.restrictions(memberId).completionLock().groupId()).isNull();
        }
    }

    @Test
    void active_pool_검증은_과거_완료_제한보다_먼저_적용한다() {
        entries.enter(MEMBER_ID, request());
        insertTerminalGroup(MEMBER_ID, "COMPLETED", "COMPLETED",
                NOW.minusMinutes(10), NOW.minusMinutes(1));

        assertError(MEMBER_ID, ErrorCode.MATCHING_CONFLICT);
    }

    @Test
    void 동일_회원의_동시_신청은_회원_row_lock으로_한_건만_성공한다() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> calls = List.of(
                    () -> callEntry(MEMBER_ID + 2),
                    () -> callEntry(MEMBER_ID + 2)
            );
            var futures = executor.invokeAll(calls);
            long successes = 0;
            long conflicts = 0;
            for (var future : futures) {
                Object value = future.get(10, TimeUnit.SECONDS);
                if (value instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.MATCHING_CONFLICT) {
                    conflicts++;
                } else {
                    successes++;
                }
            }
            assertThat(successes).isOne();
            assertThat(conflicts).isOne();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM match_pools WHERE member_id = ?",
                    Integer.class,
                    MEMBER_ID + 2
            )).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    private Object callEntry(long memberId) {
        try {
            return entries.enter(memberId, request());
        } catch (BusinessException exception) {
            return exception;
        }
    }

    private void assertError(long memberId, ErrorCode expected) {
        assertThatThrownBy(() -> entries.enter(memberId, request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private MatchPoolEntryRequest request() {
        return new MatchPoolEntryRequest(FESTIVAL_ID, 2, false, List.of());
    }

    private void insertCheckin(long memberId, OffsetDateTime expiresAt, String status) {
        jdbc.update("""
                INSERT INTO festival_checkins(
                    member_id, festival_id, distance_meters, status,
                    checked_in_at, expires_at, created_at, updated_at
                ) VALUES (?, ?, 100, ?, ?, ?, ?, ?)
                """, memberId, FESTIVAL_ID, status, NOW.minusMinutes(5), expiresAt,
                NOW.minusMinutes(5), NOW.minusMinutes(5));
    }

    private void insertTerminalGroup(
            long memberId,
            String groupStatus,
            String memberStatus,
            OffsetDateTime confirmedAt,
            OffsetDateTime completedAt
    ) {
        long attemptId = 9_320_000L + memberId;
        long groupId = 9_330_000L + memberId;
        jdbc.update("""
                INSERT INTO match_attempts(
                    id, festival_id, target_group_size, status, score, created_by,
                    started_at, expires_at, confirmed_at, created_at, updated_at
                ) VALUES (?, ?, 2, 'CONFIRMED', 0, 'SCHEDULER', ?, ?, ?, ?, ?)
                """, attemptId, FESTIVAL_ID, confirmedAt.minusMinutes(1), confirmedAt.plusMinutes(1),
                confirmedAt, confirmedAt.minusMinutes(1), confirmedAt);
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count,
                    confirmed_at, completed_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 2, ?, ?, ?, ?)
                """, groupId, attemptId, FESTIVAL_ID, groupStatus, confirmedAt, completedAt,
                confirmedAt, completedAt == null ? confirmedAt : completedAt);
        jdbc.update("""
                INSERT INTO match_group_members(
                    group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES (?, ?, ?, false, ?, ?)
                """, groupId, memberId, memberStatus, confirmedAt,
                completedAt == null ? confirmedAt : completedAt);
    }

    private void cleanupMatchingHistory(long memberId) {
        jdbc.update("DELETE FROM match_group_members WHERE member_id=?", memberId);
        jdbc.update("DELETE FROM match_groups WHERE id=?", 9_330_000L + memberId);
        jdbc.update("DELETE FROM match_attempts WHERE id=?", 9_320_000L + memberId);
    }

    private void cleanup() {
        for (long memberId = MEMBER_ID; memberId <= MEMBER_ID + 3; memberId++) {
            cleanupMatchingHistory(memberId);
        }
        jdbc.update("DELETE FROM match_cooldowns WHERE member_id BETWEEN ? AND ?", MEMBER_ID, MEMBER_ID + 3);
        jdbc.update("DELETE FROM match_pools WHERE member_id BETWEEN ? AND ?", MEMBER_ID, MEMBER_ID + 3);
        jdbc.update("DELETE FROM festival_checkins WHERE member_id BETWEEN ? AND ?", MEMBER_ID, MEMBER_ID + 3);
        jdbc.update("DELETE FROM festival_meeting_points WHERE festival_id = ?", FESTIVAL_ID);
        jdbc.update("DELETE FROM members WHERE id BETWEEN ? AND ?", MEMBER_ID, MEMBER_ID + 3);
        jdbc.update("DELETE FROM festivals WHERE id = ?", FESTIVAL_ID);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedMatchingRestClock() {
            return Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
