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

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
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

    private void cleanup() {
        jdbc.update("DELETE FROM match_cooldowns WHERE member_id BETWEEN ? AND ?", MEMBER_ID, MEMBER_ID + 3);
        jdbc.update("DELETE FROM match_pools WHERE member_id BETWEEN ? AND ?", MEMBER_ID, MEMBER_ID + 3);
        jdbc.update("DELETE FROM festival_checkins WHERE member_id BETWEEN ? AND ?", MEMBER_ID, MEMBER_ID + 3);
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
