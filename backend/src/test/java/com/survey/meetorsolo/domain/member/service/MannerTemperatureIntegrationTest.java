package com.survey.meetorsolo.domain.member.service;

import static com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixture.NOW;
import static org.assertj.core.api.Assertions.*;

import com.survey.meetorsolo.domain.member.policy.MannerTemperaturePolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
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
 * 매너온도 상승 경로 통합 검증({@code docs/19} 4.9).
 *
 * <p>실제 PostgreSQL로 확인해야 하는 것이 둘이다. 만남 완료 보상의 중복 지급을 막는
 * {@code uq_manner_temperature_events_match_completed} 제약과, 회복 대상 조회가 두 테이블
 * ({@code manner_temperature_events}, {@code match_penalty_events})의 최신 시각을 함께 보는
 * SQL이다. 둘 다 애플리케이션 코드만으로는 검증되지 않는다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=manner-temperature-integration-test-secret",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        "app.member.manner-temperature-recovery-scheduler-enabled=false"
})
@Testcontainers
@Import(MannerTemperatureIntegrationTest.FixedClockConfiguration.class)
@Sql(
        scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class MannerTemperatureIntegrationTest {

    private static final long ME = 9_110_001L;
    private static final long PARTNER = 9_110_002L;
    private static final long GROUP_ID = 9_180_001L;
    private static final OffsetDateTime TEST_NOW = NOW.plusSeconds(10);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired MannerTemperatureRewardService rewards;
    @Autowired MannerTemperatureRecoveryService recovery;
    @Autowired JdbcTemplate jdbc;

    // --- 만남 완료 보상 ---

    @Test
    void 완료한_참여자_전원의_온도를_올리고_이력을_남긴다() {
        insertCompletedGroup();
        setTemperature(ME, "34.50");
        setTemperature(PARTNER, "34.50");

        assertThat(rewards.rewardCompletedMembers(GROUP_ID, List.of(ME, PARTNER), TEST_NOW))
                .isEqualTo(2);

        assertThat(temperature(ME)).isEqualByComparingTo("35.00");
        assertThat(temperature(PARTNER)).isEqualByComparingTo("35.00");
        assertThat(eventCount(ME, "MATCH_COMPLETED")).isOne();
    }

    /**
     * 완료 API는 반복 호출되는 것이 정상 흐름이다(마지막 도착자 외의 회원이 새로고침).
     * 보상이 그때마다 지급되면 온도가 무한히 오른다.
     */
    @Test
    void 같은_그룹의_보상은_두_번_지급되지_않는다() {
        insertCompletedGroup();
        setTemperature(ME, "34.50");

        rewards.rewardCompletedMembers(GROUP_ID, List.of(ME), TEST_NOW);
        assertThat(rewards.rewardCompletedMembers(GROUP_ID, List.of(ME), TEST_NOW)).isZero();

        assertThat(temperature(ME)).isEqualByComparingTo("35.00");
        assertThat(eventCount(ME, "MATCH_COMPLETED")).isOne();
    }

    /** 이미 상한이면 아무 일도 일어나지 않은 사건을 이력에 남기지 않는다. */
    @Test
    void 상한인_회원에게는_이력을_남기지_않는다() {
        insertCompletedGroup();
        setTemperature(ME, MannerTemperaturePolicy.CEILING.toPlainString());

        assertThat(rewards.rewardCompletedMembers(GROUP_ID, List.of(ME), TEST_NOW)).isZero();

        assertThat(temperature(ME)).isEqualByComparingTo(MannerTemperaturePolicy.CEILING);
        assertThat(eventCount(ME, "MATCH_COMPLETED")).isZero();
    }

    // --- 시간 경과 회복 ---

    @Test
    void 마지막_변동에서_30일이_지나면_회복한다() {
        setTemperature(ME, "30.00");
        insertReportPenalty(ME, TEST_NOW.minusDays(31));

        assertThat(recovery.recoverBatch(10)).isEqualTo(1);

        assertThat(temperature(ME)).isEqualByComparingTo("30.50");
        assertThat(eventCount(ME, "TIME_RECOVERY")).isOne();
    }

    /** 하강 직후에 바로 회복이 시작되면 제재 효과가 사라진다. */
    @Test
    void 마지막_변동이_30일_이내면_회복하지_않는다() {
        setTemperature(ME, "30.00");
        insertReportPenalty(ME, TEST_NOW.minusDays(29));

        assertThat(recovery.recoverBatch(10)).isZero();
        assertThat(temperature(ME)).isEqualByComparingTo("30.00");
    }

    /**
     * 회복 기준 시각은 하강({@code match_penalty_events})과 상승
     * ({@code manner_temperature_events}) 중 더 최근 쪽이다. 상승만 보면 주기가 무너지고
     * 하강만 보면 회복이 매 batch마다 일어난다.
     */
    @Test
    void 직전_회복에서_30일이_지나지_않으면_다시_회복하지_않는다() {
        setTemperature(ME, "30.00");
        insertReportPenalty(ME, TEST_NOW.minusDays(60));

        assertThat(recovery.recoverBatch(10)).isEqualTo(1);
        // 방금 남긴 TIME_RECOVERY 이력이 새 기준 시각이 된다.
        assertThat(recovery.recoverBatch(10)).isZero();
        assertThat(temperature(ME)).isEqualByComparingTo("30.50");
    }

    /**
     * 시간 경과 회복은 시작값까지만이다. 상한까지 올리면 아무 활동도 하지 않은 회원이 가만히
     * 있다가 상한에 도달해 지표가 "가입한 지 얼마나 됐나"를 뜻하게 된다.
     */
    @Test
    void 시작값에_도달하면_시간이_더_지나도_회복하지_않는다() {
        setTemperature(ME, MannerTemperaturePolicy.INITIAL.toPlainString());
        insertReportPenalty(ME, TEST_NOW.minusDays(365));

        assertThat(recovery.recoverBatch(10)).isZero();
        assertThat(temperature(ME)).isEqualByComparingTo(MannerTemperaturePolicy.INITIAL);
    }

    @Test
    void 시작값_바로_아래에서는_넘지_않는_만큼만_올린다() {
        setTemperature(ME, "36.30");
        insertReportPenalty(ME, TEST_NOW.minusDays(31));

        assertThat(recovery.recoverBatch(10)).isEqualTo(1);
        assertThat(temperature(ME)).isEqualByComparingTo(MannerTemperaturePolicy.INITIAL);
    }

    /** 탈퇴·삭제 회원은 다시 매칭에 들어올 일이 없어 온도를 올릴 이유가 없다. */
    @Test
    void 탈퇴_회원은_회복_대상이_아니다() {
        setTemperature(ME, "30.00");
        insertReportPenalty(ME, TEST_NOW.minusDays(31));
        jdbc.update("UPDATE members SET status='DELETED' WHERE id=?", ME);

        assertThat(recovery.recoverBatch(10)).isZero();
        assertThat(temperature(ME)).isEqualByComparingTo("30.00");
    }

    /** 변동 이력이 아예 없으면 가입 시각을 기준으로 센다. */
    @Test
    void 변동_이력이_없어도_가입_시각_기준으로_회복한다() {
        setTemperature(ME, "30.00");
        jdbc.update("UPDATE members SET created_at=? WHERE id=?", TEST_NOW.minusDays(100), ME);

        assertThat(recovery.recoverBatch(10)).isEqualTo(1);
        assertThat(temperature(ME)).isEqualByComparingTo("30.50");
    }

    // --- fixture ---

    private void insertCompletedGroup() {
        long attemptId = 9_181_001L;
        jdbc.update("""
                INSERT INTO match_attempts(
                    id, festival_id, target_group_size, status, score, created_by,
                    started_at, expires_at, created_at, updated_at
                ) VALUES (?, 9100001, 2, 'CONFIRMED', 80.00, 'SCHEDULER', ?, ?, ?, ?)
                """, attemptId, NOW, NOW.plusMinutes(2), NOW, NOW);
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count,
                    confirmed_at, completed_at, created_at, updated_at
                ) VALUES (?, ?, 9100001, 'COMPLETED', 2, ?, ?, ?, ?)
                """, GROUP_ID, attemptId, NOW, TEST_NOW, NOW, NOW);
        jdbc.update("""
                INSERT INTO match_group_members(
                    group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES (?, ?, 'COMPLETED', true, ?, ?), (?, ?, 'COMPLETED', true, ?, ?)
                """, GROUP_ID, ME, NOW, NOW, GROUP_ID, PARTNER, NOW, NOW);
    }

    private void insertReportPenalty(long memberId, OffsetDateTime createdAt) {
        jdbc.update("""
                INSERT INTO match_penalty_events(
                    member_id, event_type, score_delta, manner_temperature_delta, reason, created_at
                ) VALUES (?, 'REPORT_CONFIRMED', 5, -2.00, '테스트', ?)
                """, memberId, createdAt);
    }

    private void setTemperature(long memberId, String value) {
        jdbc.update("UPDATE members SET manner_temperature=? WHERE id=?",
                new BigDecimal(value), memberId);
    }

    private BigDecimal temperature(long memberId) {
        return jdbc.queryForObject(
                "SELECT manner_temperature FROM members WHERE id=?", BigDecimal.class, memberId);
    }

    private int eventCount(long memberId, String eventType) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM manner_temperature_events
                WHERE member_id=? AND event_type=?
                """, Integer.class, memberId, eventType);
        return count == null ? 0 : count;
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock clock() {
            return Clock.fixed(TEST_NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
