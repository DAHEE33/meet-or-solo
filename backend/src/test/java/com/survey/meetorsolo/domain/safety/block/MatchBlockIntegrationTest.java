package com.survey.meetorsolo.domain.safety.block;

import static com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.matching.service.MatchingBatchReader;
import com.survey.meetorsolo.domain.safety.block.dto.MatchBlockResponse;
import com.survey.meetorsolo.domain.safety.block.service.MatchBlockService;
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
        "app.jwt.secret=match-block-integration-test-secret",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(MatchBlockIntegrationTest.FixedClockConfiguration.class)
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchBlockIntegrationTest {

    private static final long GROUP_ID = 9_170_001L;
    private static final long SECOND_GROUP_ID = 9_170_002L;
    private static final long SECOND_ATTEMPT_ID = 9_130_002L;
    private static final long BLOCKER_ID = 9_110_001L;
    private static final long BLOCKED_ID = 9_110_002L;
    private static final long OUTSIDER_ID = 9_110_003L;
    private static final OffsetDateTime TEST_NOW = NOW.plusSeconds(10);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired MatchBlockService service;
    @Autowired MatchPoolRepository pools;
    @Autowired MatchingBatchReader batchReader;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 같은_group_상대를_차단하고_민감한_내부정보를_응답하지_않는다() throws Exception {
        insertGroup(GROUP_ID, "IN_PROGRESS", null, null, BLOCKER_ID, BLOCKED_ID);

        mockMvc.perform(post("/api/match-groups/{groupId}/blocks", GROUP_ID)
                        .cookie(cookie(BLOCKER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockerMemberId":9110003,"blockedMemberId":9110002,"reason":"free"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.blockId").isNumber())
                .andExpect(jsonPath("$.data.blockedMemberId").value(BLOCKED_ID))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.blockerMemberId").doesNotExist())
                .andExpect(jsonPath("$.data.reason").doesNotExist());

        assertThat(blockCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT reason FROM user_blocks
                WHERE blocker_member_id=? AND blocked_member_id=?
                """, String.class, BLOCKER_ID, BLOCKED_ID)).isEqualTo("MATCH_ROOM_MEMBER_BLOCK");
    }

    @Test
    void 본인_차단은_거절하고_저장하지_않는다() {
        insertGroup(GROUP_ID, "CONFIRMED", null, null, BLOCKER_ID, BLOCKED_ID);
        assertError(BLOCKER_ID, GROUP_ID, BLOCKER_ID, ErrorCode.BLOCK_INVALID_REQUEST);
        assertThat(blockCount()).isEqualTo(2);
    }

    @Test
    void 인증_cookie가_없으면_거절한다() throws Exception {
        insertGroup(GROUP_ID, "CONFIRMED", null, null, BLOCKER_ID, BLOCKED_ID);
        mockMvc.perform(post("/api/match-groups/{groupId}/blocks", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockedMemberId\":9110002}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        assertThat(blockCount()).isEqualTo(2);
    }

    @Test
    void 양쪽_참여이력과_IDOR은_같은_404로_거절한다() throws Exception {
        insertGroup(GROUP_ID, "CONFIRMED", null, null, BLOCKED_ID, OUTSIDER_ID);
        assertError(BLOCKER_ID, GROUP_ID, BLOCKED_ID, ErrorCode.BLOCK_RESOURCE_NOT_FOUND);
        assertError(OUTSIDER_ID, GROUP_ID, BLOCKER_ID, ErrorCode.BLOCK_RESOURCE_NOT_FOUND);

        mockMvc.perform(post("/api/match-groups/{groupId}/blocks", 9_179_999L)
                        .cookie(cookie(BLOCKER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockedMemberId\":9110002}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOCK_RESOURCE_NOT_FOUND"));
        assertThat(blockCount()).isEqualTo(2);
    }

    @Test
    void 진행중과_종료후_30일_경계를_허용한다() {
        insertGroup(GROUP_ID, "CONFIRMED", null, null, BLOCKER_ID, BLOCKED_ID);
        assertThat(service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID).blockId()).isPositive();
        jdbc.update("DELETE FROM user_blocks WHERE blocker_member_id=? AND blocked_member_id=?",
                BLOCKER_ID, BLOCKED_ID);
        jdbc.update("DELETE FROM match_group_members WHERE group_id=?", GROUP_ID);
        jdbc.update("DELETE FROM match_groups WHERE id=?", GROUP_ID);

        insertGroup(GROUP_ID, "COMPLETED", TEST_NOW.minusDays(30).plusSeconds(1), null,
                BLOCKER_ID, BLOCKED_ID);
        assertThat(service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID).blockId()).isPositive();
        jdbc.update("DELETE FROM user_blocks WHERE blocker_member_id=? AND blocked_member_id=?",
                BLOCKER_ID, BLOCKED_ID);
        jdbc.update("UPDATE match_groups SET completed_at=? WHERE id=?",
                TEST_NOW.minusDays(30), GROUP_ID);
        assertThat(service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID).blockId()).isPositive();
        jdbc.update("DELETE FROM user_blocks WHERE blocker_member_id=? AND blocked_member_id=?",
                BLOCKER_ID, BLOCKED_ID);
        jdbc.update("""
                UPDATE match_groups
                SET status='CANCELLED', completed_at=NULL, cancelled_at=?
                WHERE id=?
                """, TEST_NOW.minusDays(30), GROUP_ID);
        assertThat(service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID).blockId()).isPositive();
    }

    @Test
    void 종료후_30일_초과와_terminal_timestamp_누락은_거절한다() {
        insertGroup(GROUP_ID, "COMPLETED", TEST_NOW.minusDays(30).minusSeconds(1), null,
                BLOCKER_ID, BLOCKED_ID);
        assertError(BLOCKER_ID, GROUP_ID, BLOCKED_ID, ErrorCode.BLOCK_WINDOW_EXPIRED);
        jdbc.update("UPDATE match_groups SET completed_at=NULL WHERE id=?", GROUP_ID);
        assertError(BLOCKER_ID, GROUP_ID, BLOCKED_ID, ErrorCode.BLOCK_CONFLICT);
        assertThat(blockCount()).isEqualTo(2);
    }

    @Test
    void 동일_요청과_다른_group_반복은_기존_row와_created_at_reason을_유지한다() {
        insertGroup(GROUP_ID, "COMPLETED", TEST_NOW, null, BLOCKER_ID, BLOCKED_ID);
        jdbc.update("UPDATE match_group_members SET status='COMPLETED' WHERE group_id=?", GROUP_ID);
        insertAttempt(SECOND_ATTEMPT_ID);
        insertGroup(SECOND_GROUP_ID, SECOND_ATTEMPT_ID, "IN_PROGRESS", null, null,
                BLOCKER_ID, BLOCKED_ID);
        MatchBlockResponse first = service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID);
        jdbc.update("UPDATE user_blocks SET reason='PRESERVED' WHERE id=?", first.blockId());

        MatchBlockResponse sameGroup = service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID);
        MatchBlockResponse otherGroup = service.block(BLOCKER_ID, SECOND_GROUP_ID, BLOCKED_ID);

        assertThat(sameGroup).isEqualTo(first);
        assertThat(otherGroup).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT reason FROM user_blocks WHERE id=?",
                String.class, first.blockId())).isEqualTo("PRESERVED");
        assertThat(blockCount()).isEqualTo(3);
    }

    @Test
    void 동시_동일_요청도_row_한_건과_같은_snapshot을_반환한다() throws Exception {
        insertGroup(GROUP_ID, "CONFIRMED", null, null, BLOCKER_ID, BLOCKED_ID);
        int workers = 6;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MatchBlockResponse>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<MatchBlockResponse> responses = new ArrayList<>();
            for (Future<MatchBlockResponse> future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }
            assertThat(responses).containsOnly(responses.get(0));
            assertThat(blockCount()).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 차단_직후_양방향_후보와_scheduler_batch_pair에서_제외한다() {
        insertGroup(GROUP_ID, "IN_PROGRESS", null, null, BLOCKER_ID, BLOCKED_ID);
        service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID);

        assertThat(pools.findEligibleWaitingCandidates(9_100_001L, BLOCKER_ID, NOW))
                .extracting(pool -> pool.getMemberId()).doesNotContain(BLOCKED_ID);
        assertThat(pools.findEligibleWaitingCandidates(9_100_001L, BLOCKED_ID, NOW))
                .extracting(pool -> pool.getMemberId()).doesNotContain(BLOCKER_ID);
        jdbc.update("UPDATE match_pools SET status='LOCKED', lock_token='block-batch' "
                + "WHERE member_id IN (?,?)", BLOCKER_ID, BLOCKED_ID);
        assertThat(batchReader.read("block-batch").blockedPairs())
                .contains(MatchingBatchReader.MemberPair.of(BLOCKER_ID, BLOCKED_ID));
    }

    @Test
    void 차단은_penalty_cooldown_점수_event를_변경하지_않는다() {
        insertGroup(GROUP_ID, "IN_PROGRESS", null, null, BLOCKER_ID, BLOCKED_ID);
        NumberSnapshot before = numberSnapshot();
        service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID);

        assertThat(numberSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_events WHERE group_id=?",
                Integer.class, GROUP_ID)).isZero();
    }

    @Test
    void insert_실패는_부분저장_없이_rollback한다() {
        insertGroup(GROUP_ID, "CONFIRMED", null, null, BLOCKER_ID, BLOCKED_ID);
        jdbc.execute("""
                CREATE FUNCTION fail_block_insert() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced block failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_block_insert BEFORE INSERT ON user_blocks
                FOR EACH ROW EXECUTE FUNCTION fail_block_insert()
                """);
        try {
            assertThatThrownBy(() -> service.block(BLOCKER_ID, GROUP_ID, BLOCKED_ID))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_block_insert ON user_blocks");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_block_insert()");
        }
        assertThat(blockCount()).isEqualTo(2);
    }

    private void assertError(long blockerId, long groupId, long blockedId, ErrorCode errorCode) {
        assertThatThrownBy(() -> service.block(blockerId, groupId, blockedId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private void insertGroup(long groupId, String status, OffsetDateTime completedAt,
                             OffsetDateTime cancelledAt, long firstMemberId, long secondMemberId) {
        insertGroup(groupId, 9_130_001L, status, completedAt, cancelledAt,
                firstMemberId, secondMemberId);
    }

    private void insertGroup(long groupId, long attemptId, String status,
                             OffsetDateTime completedAt, OffsetDateTime cancelledAt,
                             long firstMemberId, long secondMemberId) {
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count,
                    confirmed_at, completed_at, cancelled_at, created_at, updated_at
                ) VALUES (?, ?, 9100001, ?, 2, ?, ?, ?, ?, ?)
                """, groupId, attemptId, status, NOW, completedAt, cancelledAt, NOW, NOW);
        long suffix = groupId - 9_170_000L;
        jdbc.update("""
                INSERT INTO match_group_members(
                    id, group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES (?, ?, ?, 'JOINED', true, ?, ?),
                         (?, ?, ?, 'JOINED', true, ?, ?)
                """, 9_180_000L + suffix * 10, groupId, firstMemberId, NOW, NOW,
                9_180_001L + suffix * 10, groupId, secondMemberId, NOW, NOW);
    }

    private void insertAttempt(long attemptId) {
        jdbc.update("""
                INSERT INTO match_attempts(
                    id, festival_id, target_group_size, status, score, created_by,
                    started_at, expires_at, created_at, updated_at
                ) VALUES (?, 9100001, 2, 'CONFIRMED', 0, 'SCHEDULER', ?, ?, ?, ?)
                """, attemptId, NOW.minusMinutes(1), NOW.plusMinutes(1), NOW, NOW);
    }

    private int blockCount() {
        return jdbc.queryForObject("SELECT count(*) FROM user_blocks", Integer.class);
    }

    private NumberSnapshot numberSnapshot() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM match_penalty_events) penalty_count,
                       (SELECT count(*) FROM match_cooldowns) cooldown_count,
                       penalty_score, manner_temperature
                FROM members WHERE id=?
                """, (rs, rowNum) -> new NumberSnapshot(
                rs.getInt("penalty_count"), rs.getInt("cooldown_count"),
                rs.getInt("penalty_score"), rs.getBigDecimal("manner_temperature")), BLOCKED_ID);
    }

    private jakarta.servlet.http.Cookie cookie(long memberId) {
        return new jakarta.servlet.http.Cookie(
                "access_token", jwtProvider.createAccessToken(memberId, "ACTIVE"));
    }

    private record NumberSnapshot(
            int penaltyCount, int cooldownCount, int penaltyScore,
            java.math.BigDecimal mannerTemperature) {
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedBlockClock() {
            return Clock.fixed(TEST_NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
