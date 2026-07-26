package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@Testcontainers
@Import(PoolEntryMatchingOrchestrationServiceIntegrationTest.FixedClockConfiguration.class)
class PoolEntryMatchingOrchestrationServiceIntegrationTest {

    private static final long FESTIVAL_ID = 9_700_001L;
    private static final long OTHER_FESTIVAL_ID = 9_700_002L;
    private static final long REQUESTER_MEMBER_ID = 9_710_001L;
    private static final long CANDIDATE_MEMBER_ID = 9_710_002L;
    private static final long OTHER_MEMBER_ID = 9_710_003L;
    private static final long REQUESTER_POOL_ID = 9_720_001L;
    private static final long CANDIDATE_POOL_ID = 9_720_002L;
    private static final long OTHER_POOL_ID = 9_720_003L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-26T15:00:00+09:00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private PoolEntryMatchingOrchestrationService poolEntryOrchestration;

    @Autowired
    private MatchingOrchestrationService schedulerOrchestration;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        insertFestival(FESTIVAL_ID);
        insertFestival(OTHER_FESTIVAL_ID);
        insertMember(REQUESTER_MEMBER_ID);
        insertMember(CANDIDATE_MEMBER_ID);
        insertMember(OTHER_MEMBER_ID);
        long requesterCheckin = insertCheckin(REQUESTER_MEMBER_ID, FESTIVAL_ID, "ACTIVE", NOW.plusHours(1));
        long candidateCheckin = insertCheckin(CANDIDATE_MEMBER_ID, FESTIVAL_ID, "ACTIVE", NOW.plusHours(1));
        long otherCheckin = insertCheckin(OTHER_MEMBER_ID, OTHER_FESTIVAL_ID, "ACTIVE", NOW.plusHours(1));
        insertPool(REQUESTER_POOL_ID, REQUESTER_MEMBER_ID, FESTIVAL_ID, requesterCheckin, NOW.minusSeconds(20));
        insertPool(CANDIDATE_POOL_ID, CANDIDATE_MEMBER_ID, FESTIVAL_ID, candidateCheckin, NOW.minusSeconds(30));
        insertPool(OTHER_POOL_ID, OTHER_MEMBER_ID, OTHER_FESTIVAL_ID, otherCheckin, NOW.minusSeconds(40));
    }

    @AfterEach
    void tearDown() {
        dropFailureTrigger();
        jdbc.update("DELETE FROM match_responses");
        jdbc.update("DELETE FROM match_proposals");
        jdbc.update("DELETE FROM match_attempt_members");
        jdbc.update("DELETE FROM match_groups");
        jdbc.update("DELETE FROM match_attempts");
        jdbc.update("DELETE FROM match_pools WHERE id BETWEEN ? AND ?", REQUESTER_POOL_ID, OTHER_POOL_ID);
        jdbc.update(
                "DELETE FROM festival_checkins WHERE member_id BETWEEN ? AND ?",
                REQUESTER_MEMBER_ID,
                OTHER_MEMBER_ID
        );
        jdbc.update("DELETE FROM members WHERE id BETWEEN ? AND ?", REQUESTER_MEMBER_ID, OTHER_MEMBER_ID);
        jdbc.update("DELETE FROM festivals WHERE id IN (?, ?)", FESTIVAL_ID, OTHER_FESTIVAL_ID);
    }

    @Test
    void requester를_포함한_같은_축제_pool만_PROPOSED로_전환하고_POOL_ENTRY_attempt를_생성한다() {
        MatchingOrchestrationResult result = runPoolEntry();

        assertThat(result.createdAttemptIds()).hasSize(1);
        assertThat(status(REQUESTER_POOL_ID)).isEqualTo("PROPOSED");
        assertThat(status(CANDIDATE_POOL_ID)).isEqualTo("PROPOSED");
        assertThat(status(OTHER_POOL_ID)).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject(
                "SELECT created_by FROM match_attempts WHERE id = ?",
                String.class,
                result.createdAttemptIds().get(0)
        )).isEqualTo("POOL_ENTRY");
        assertThat(count("match_attempt_members")).isEqualTo(2);
        assertThat(count("match_proposals")).isEqualTo(2);
    }

    @Test
    void 후보가_부족하면_requester는_WAITING으로_복구되고_attempt를_만들지_않는다() {
        jdbc.update("DELETE FROM match_pools WHERE id = ?", CANDIDATE_POOL_ID);

        MatchingOrchestrationResult result = runPoolEntry();

        assertThat(result.createdAttemptIds()).isEmpty();
        assertThat(status(REQUESTER_POOL_ID)).isEqualTo("WAITING");
        assertThat(count("match_attempts")).isZero();
    }

    @Test
    void 동일_event를_다시_실행해도_attempt를_중복_생성하지_않는다() {
        runPoolEntry();
        runPoolEntry();

        assertThat(count("match_attempts")).isOne();
        assertThat(count("match_proposals")).isEqualTo(2);
    }

    @Test
    void 비활성_checkin_후보는_선점하지_않고_requester를_WAITING으로_유지한다() {
        jdbc.update(
                "UPDATE festival_checkins SET status = 'CANCELLED' WHERE member_id = ?",
                CANDIDATE_MEMBER_ID
        );

        MatchingOrchestrationResult result = runPoolEntry();

        assertThat(result.createdAttemptIds()).isEmpty();
        assertThat(status(REQUESTER_POOL_ID)).isEqualTo("WAITING");
        assertThat(status(CANDIDATE_POOL_ID)).isEqualTo("WAITING");
    }

    @Test
    void proposal_생성_실패는_group_transaction을_rollback하고_claim한_pool을_WAITING으로_release한다() {
        installProposalFailureTrigger();

        MatchingOrchestrationResult result = runPoolEntry();

        assertThat(result.failedGroupCount()).isOne();
        assertThat(status(REQUESTER_POOL_ID)).isEqualTo("WAITING");
        assertThat(status(CANDIDATE_POOL_ID)).isEqualTo("WAITING");
        assertThat(count("match_attempts")).isZero();
        assertThat(count("match_proposals")).isZero();
    }

    @Test
    void trigger와_Scheduler가_동시에_실행되어도_같은_pool의_attempt는_하나만_생성된다() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<MatchingOrchestrationResult>> calls = List.of(
                    this::runPoolEntry,
                    schedulerOrchestration::runTick
            );
            var futures = executor.invokeAll(calls);
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }

            assertThat(count("match_attempts")).isOne();
            assertThat(count("match_attempt_members")).isEqualTo(2);
            assertThat(count("match_proposals")).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(DISTINCT member_id) FROM match_attempt_members",
                    Integer.class
            )).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void 두_pool_entry_trigger가_동시에_실행되어도_같은_pool을_중복_선점하지_않는다() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<MatchingOrchestrationResult>> calls = List.of(
                    () -> poolEntryOrchestration.run(
                            REQUESTER_POOL_ID,
                            REQUESTER_MEMBER_ID,
                            FESTIVAL_ID
                    ),
                    () -> poolEntryOrchestration.run(
                            CANDIDATE_POOL_ID,
                            CANDIDATE_MEMBER_ID,
                            FESTIVAL_ID
                    )
            );
            var futures = executor.invokeAll(calls);
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }

            assertThat(count("match_attempts")).isOne();
            assertThat(count("match_attempt_members")).isEqualTo(2);
            assertThat(count("match_proposals")).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void Scheduler_fallback은_SCHEDULER_attempt를_유지한다() {
        MatchingOrchestrationResult result = schedulerOrchestration.runTick();

        assertThat(result.createdAttemptIds()).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT created_by FROM match_attempts WHERE id = ?",
                String.class,
                result.createdAttemptIds().get(0)
        )).isEqualTo("SCHEDULER");
    }

    private MatchingOrchestrationResult runPoolEntry() {
        return poolEntryOrchestration.run(REQUESTER_POOL_ID, REQUESTER_MEMBER_ID, FESTIVAL_ID);
    }

    private void insertFestival(long festivalId) {
        jdbc.update("""
                INSERT INTO festivals(
                    id, content_id, content_type_id, title, area_code,
                    status, last_synced_at, created_at, updated_at
                ) VALUES (?, ?, 15, ?, '32', 'ACTIVE', ?, ?, ?)
                """, festivalId, "trigger-" + festivalId, "trigger 축제 " + festivalId, NOW, NOW, NOW);
    }

    private void insertMember(long memberId) {
        jdbc.update("""
                INSERT INTO members(
                    id, provider, provider_user_id, nickname, role, status,
                    penalty_score, created_at, updated_at
                ) VALUES (?, 'KAKAO', ?, ?, 'USER', 'ACTIVE', 0, ?, ?)
                """, memberId, "trigger-" + memberId, "회원" + memberId, NOW, NOW);
    }

    private long insertCheckin(
            long memberId,
            long festivalId,
            String status,
            OffsetDateTime expiresAt
    ) {
        return jdbc.queryForObject("""
                INSERT INTO festival_checkins(
                    member_id, festival_id, distance_meters, status,
                    checked_in_at, expires_at, created_at, updated_at
                ) VALUES (?, ?, 10, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, memberId, festivalId, status, NOW.minusMinutes(1), expiresAt, NOW, NOW);
    }

    private void insertPool(
            long poolId,
            long memberId,
            long festivalId,
            long checkinId,
            OffsetDateTime enteredAt
    ) {
        jdbc.update("""
                INSERT INTO match_pools(
                    id, member_id, festival_id, checkin_id, preferred_group_size,
                    allow_minimum_two, tags, status, entered_at, search_expires_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 2, false, '[]'::jsonb, 'WAITING', ?, ?, ?, ?)
                """, poolId, memberId, festivalId, checkinId, enteredAt, NOW.plusMinutes(1), enteredAt, enteredAt);
    }

    private String status(long poolId) {
        return jdbc.queryForObject(
                "SELECT status FROM match_pools WHERE id = ?",
                String.class,
                poolId
        );
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private void installProposalFailureTrigger() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION test_pool_entry_proposal_failure_fn()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'forced proposal failure';
                END
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER test_pool_entry_proposal_failure
                BEFORE INSERT ON match_proposals
                FOR EACH ROW EXECUTE FUNCTION test_pool_entry_proposal_failure_fn()
                """);
    }

    private void dropFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_pool_entry_proposal_failure ON match_proposals");
        jdbc.execute("DROP FUNCTION IF EXISTS test_pool_entry_proposal_failure_fn()");
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedPoolEntryMatchingClock() {
            return Clock.fixed(Instant.parse("2026-07-26T06:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
