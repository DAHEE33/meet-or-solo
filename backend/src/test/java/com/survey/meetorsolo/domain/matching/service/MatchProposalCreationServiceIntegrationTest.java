package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchingCandidate;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate") @Testcontainers
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchProposalCreationServiceIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026,7,17,15,0,0,0, ZoneOffset.ofHours(9));
    private static final String TOKEN = "create-token";
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Autowired MatchProposalCreationService service; @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @ParameterizedTest @ValueSource(ints = {2,3,4})
    void 정확한_인원으로_attempt_member_proposal과_PROPOSED_pool을_원자_생성한다(int size) {
        MatchGroupCombination group = prepareGroup(size);
        long attemptId = service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)).attemptId();
        assertThat(jdbc.queryForMap("SELECT status,score,started_at,expires_at FROM match_attempts WHERE id=?", attemptId))
                .containsEntry("status", "WAITING_RESPONSES").containsEntry("score", new BigDecimal("100.00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_attempt_members WHERE attempt_id=? AND status='PROPOSED' AND member_score=100.00", Integer.class, attemptId)).isEqualTo(size);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_proposals WHERE attempt_id=? AND proposal_type='INITIAL_MATCH' AND proposal_round=1 AND status='SENT' AND sent_at=? AND expires_at=?", Integer.class, attemptId, NOW, NOW.plusSeconds(30))).isEqualTo(size);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE id IN (" + ids(size) + ") AND status='PROPOSED' AND locked_at IS NULL AND lock_token IS NULL", Integer.class)).isEqualTo(size);
    }

    @Test void token이_다르면_전체를_거부하고_중간_data를_남기지_않는다() {
        MatchGroupCombination group = prepareGroup(2);
        assertThatThrownBy(() -> service.createInitial(group, "other", NOW, Duration.ofSeconds(30)))
                .isInstanceOf(MatchProposalCreationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_attempts WHERE started_at=?", Integer.class, NOW)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE lock_token=?", Integer.class, TOKEN)).isEqualTo(2);
    }

    @Test void 모든_pair의_차단과_active_cooldown을_최종_재검증한다() {
        MatchGroupCombination blocked = prepareGroup(2);
        jdbc.update("INSERT INTO user_blocks(blocker_member_id,blocked_member_id,reason) VALUES (9110002,9110006,'TEST')");
        assertThatThrownBy(() -> service.createInitial(blocked, TOKEN, NOW, Duration.ofSeconds(30)))
                .isInstanceOf(MatchProposalCreationException.class).hasMessageContaining("차단");
        jdbc.update("DELETE FROM user_blocks WHERE blocker_member_id=9110002 AND blocked_member_id=9110006");
        jdbc.update("INSERT INTO match_cooldowns(member_id,reason,status,starts_at,expires_at) VALUES (9110002,'TIMEOUT','ACTIVE',?,?)", NOW.minusSeconds(1), NOW.plusSeconds(10));
        assertThatThrownBy(() -> service.createInitial(blocked, TOKEN, NOW, Duration.ofSeconds(30)))
                .isInstanceOf(MatchProposalCreationException.class).hasMessageContaining("cooldown");
    }

    @Test void 동시_재실행은_하나의_attempt만_생성한다() throws Exception {
        MatchGroupCombination group = prepareGroup(2);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)));
            var second = workers.submit(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)));
            int successes = 0;
            for (var future : List.of(first, second)) {
                try { future.get(); successes++; } catch (ExecutionException expected) {
                    assertThat(expected.getCause()).isInstanceOf(MatchProposalCreationException.class);
                }
            }
            assertThat(successes).isOne();
        } finally {
            workers.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_attempts WHERE started_at=?", Integer.class, NOW)).isOne();
    }

    @ParameterizedTest @ValueSource(strings = {"WAITING", "EXPIRED", "PROPOSED"})
    void LOCKED가_아닌_pool을_거부한다(String status) {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("UPDATE match_pools SET status=?,locked_at=NULL,lock_token=NULL WHERE id=9120002", status);
        assertRejectedWithoutCreatedRows(group);
        assertThat(poolStatus(9_120_002L)).isEqualTo(status);
    }

    @Test void search_expires_at이_now와_같으면_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("UPDATE match_pools SET search_expires_at=? WHERE id=9120002", NOW);
        assertRejectedWithoutCreatedRows(group);
        assertLocked(9_120_002L);
    }

    @ParameterizedTest @ValueSource(strings = {"CANCELLED", "EXPIRED"})
    void 비활성_checkin을_거부한다(String status) {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("UPDATE festival_checkins SET status=? WHERE id=9120002", status);
        assertRejectedWithoutCreatedRows(group);
        assertLocked(9_120_002L);
    }

    @Test void 만료_checkin을_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("UPDATE festival_checkins SET expires_at=? WHERE id=9120002", NOW);
        assertRejectedWithoutCreatedRows(group);
        assertLocked(9_120_002L);
    }

    @Test void checkin_회원_불일치를_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("UPDATE match_pools SET checkin_id=9120006 WHERE id=9120002");
        assertRejectedWithoutCreatedRows(group);
        assertLocked(9_120_002L);
    }

    @Test void checkin_축제_불일치를_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("UPDATE festival_checkins SET festival_id=9100002 WHERE id=9120002");
        assertRejectedWithoutCreatedRows(group);
        assertLocked(9_120_002L);
    }

    @Test void 서로_다른_축제_snapshot을_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        MatchingCandidate first = group.candidates().get(0);
        MatchingCandidate second = group.candidates().get(1);
        MatchGroupCombination invalid = new MatchGroupCombination(List.of(first, candidate(second, second.memberId(), 9_100_002L, 2)), group.score());
        assertRejectedWithoutCreatedRows(invalid);
        assertLocked(9_120_002L);
    }

    @Test void 서로_다른_preferredGroupSize를_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        MatchingCandidate first = group.candidates().get(0);
        MatchingCandidate second = group.candidates().get(1);
        MatchGroupCombination invalid = new MatchGroupCombination(List.of(first, candidate(second, second.memberId(), second.festivalId(), 3)), group.score());
        assertRejectedWithoutCreatedRows(invalid);
        assertLocked(9_120_002L);
    }

    @Test void 중복_poolId를_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        MatchGroupCombination invalid = new MatchGroupCombination(List.of(group.candidates().get(0), group.candidates().get(0)), group.score());
        assertRejectedWithoutCreatedRows(invalid);
        assertLocked(9_120_002L);
    }

    @Test void 중복_memberId를_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        MatchingCandidate first = group.candidates().get(0);
        MatchingCandidate second = group.candidates().get(1);
        MatchGroupCombination invalid = new MatchGroupCombination(List.of(first,
                candidate(second, first.memberId(), second.festivalId(), second.preferredGroupSize())), group.score());
        assertRejectedWithoutCreatedRows(invalid);
        assertLocked(9_120_002L);
    }

    @Test void 요청_pool수와_잠금_조회수가_다르면_거부한다() {
        MatchGroupCombination group = prepareGroup(2);
        MatchingCandidate missing = new MatchingCandidate(99_999_999L, 9_110_006L, 9_100_001L, 2,
                false, NOW, List.of(TravelStyleCode.PHOTO));
        assertRejectedWithoutCreatedRows(new MatchGroupCombination(List.of(group.candidates().get(0), missing), group.score()));
        assertLocked(9_120_002L);
    }

    @ParameterizedTest @CsvSource({"9110002,9110006", "9110006,9110002"})
    void 정방향과_역방향_block을_모두_거부한다(long blocker, long blocked) {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("INSERT INTO user_blocks(blocker_member_id,blocked_member_id,reason) VALUES (?,?,'TEST')", blocker, blocked);
        assertRejectedWithoutCreatedRows(group);
        assertLocked(9_120_002L);
    }

    @Test void sequential_재실행은_중복_attempt와_proposal을_생성하지_않는다() {
        MatchGroupCombination group = prepareGroup(2);
        service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30));
        assertThatThrownBy(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)))
                .isInstanceOf(MatchProposalCreationException.class);
        assertThat(createdAttemptCount()).isOne();
        assertThat(createdMemberCount()).isEqualTo(2);
        assertThat(createdProposalCount()).isEqualTo(2);
    }

    @Test void attempt저장후_member_insert실패면_전체_rollback한다() {
        MatchGroupCombination group = prepareGroup(2);
        installFailureTrigger("match_attempt_members", "test_fail_member", null);
        try { assertRejectedByDatabase(group); } finally { dropFailureTrigger("match_attempt_members", "test_fail_member"); }
        assertNothingCreatedAndLocked();
    }

    @Test void proposal일부_insert실패면_attempt_member_proposal을_전체_rollback한다() {
        MatchGroupCombination group = prepareGroup(2);
        installFailureTrigger("match_proposals", "test_fail_proposal", "NEW.member_id = 9110006");
        try { assertRejectedByDatabase(group); } finally { dropFailureTrigger("match_proposals", "test_fail_proposal"); }
        assertNothingCreatedAndLocked();
    }

    @Test void pool_PROPOSED_flush실패면_생성data와_pool전이를_전체_rollback한다() {
        MatchGroupCombination group = prepareGroup(2);
        installFailureTrigger("match_pools", "test_fail_pool", "NEW.status = 'PROPOSED'");
        try { assertRejectedByDatabase(group); } finally { dropFailureTrigger("match_pools", "test_fail_pool"); }
        assertNothingCreatedAndLocked();
    }

    @Test void 외부_transaction이_rollback되어도_성공한_REQUIRES_NEW는_유지된다() {
        MatchGroupCombination group = prepareGroup(2);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30));
            status.setRollbackOnly();
        });
        assertThat(createdAttemptCount()).isOne();
        assertThat(createdMemberCount()).isEqualTo(2);
        assertThat(createdProposalCount()).isEqualTo(2);
        assertThat(poolStatus(9_120_002L)).isEqualTo("PROPOSED");
    }

    @Test void 실패한_REQUIRES_NEW는_외부_transaction과_무관하게_data를_남기지_않는다() {
        MatchGroupCombination group = prepareGroup(2);
        installFailureTrigger("match_attempt_members", "test_fail_member_outer", null);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    assertThatThrownBy(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)))
                            .isInstanceOf(RuntimeException.class));
        } finally { dropFailureTrigger("match_attempt_members", "test_fail_member_outer"); }
        assertNothingCreatedAndLocked();
    }

    private MatchGroupCombination prepareGroup(int size) {
        jdbc.update("DELETE FROM user_blocks"); jdbc.update("DELETE FROM match_cooldowns");
        List<Long> poolIds = List.of(9_120_002L, 9_120_006L, 9_120_010L, 9_120_011L).subList(0, size);
        for (long id : poolIds) jdbc.update("UPDATE match_pools SET preferred_group_size=?,status='LOCKED',locked_at=?,lock_token=?,search_expires_at=? WHERE id=?", size, NOW, TOKEN, NOW.plusMinutes(1), id);
        List<MatchingCandidate> candidates = IntStream.range(0, poolIds.size()).mapToObj(index -> {
            long id = poolIds.get(index);
            return new MatchingCandidate(id, 9_110_000L + (id - 9_120_000L), 9_100_001L,
                    size, false, NOW.minusSeconds(index), List.of(TravelStyleCode.PHOTO));
        }).toList();
        return new MatchGroupCombination(candidates, new BigDecimal("100.00"));
    }
    private String ids(int size) { return List.of("9120002", "9120006", "9120010", "9120011").subList(0, size).stream().collect(java.util.stream.Collectors.joining(",")); }

    private MatchingCandidate candidate(MatchingCandidate source, long memberId, long festivalId, int preferredSize) {
        return new MatchingCandidate(source.poolId(), memberId, festivalId, preferredSize, source.allowMinimumTwo(),
                source.enteredAt(), source.travelStyles());
    }
    private void assertRejectedWithoutCreatedRows(MatchGroupCombination group) {
        assertThatThrownBy(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)))
                .isInstanceOf(MatchProposalCreationException.class);
        assertThat(createdAttemptCount()).isZero();
        assertThat(createdMemberCount()).isZero();
        assertThat(createdProposalCount()).isZero();
        assertLocked(9_120_006L);
    }
    private void assertRejectedByDatabase(MatchGroupCombination group) {
        assertThatThrownBy(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)))
                .isInstanceOf(RuntimeException.class);
    }
    private void assertNothingCreatedAndLocked() {
        assertThat(createdAttemptCount()).isZero();
        assertThat(createdMemberCount()).isZero();
        assertThat(createdProposalCount()).isZero();
        assertLocked(9_120_002L); assertLocked(9_120_006L);
    }
    private int createdAttemptCount() { return jdbc.queryForObject("SELECT count(*) FROM match_attempts WHERE started_at=?", Integer.class, NOW); }
    private int createdMemberCount() { return jdbc.queryForObject("SELECT count(*) FROM match_attempt_members m JOIN match_attempts a ON a.id=m.attempt_id WHERE a.started_at=?", Integer.class, NOW); }
    private int createdProposalCount() { return jdbc.queryForObject("SELECT count(*) FROM match_proposals p JOIN match_attempts a ON a.id=p.attempt_id WHERE a.started_at=?", Integer.class, NOW); }
    private String poolStatus(long id) { return jdbc.queryForObject("SELECT status FROM match_pools WHERE id=?", String.class, id); }
    private void assertLocked(long id) {
        assertThat(jdbc.queryForMap("SELECT status,lock_token FROM match_pools WHERE id=?", id))
                .containsEntry("status", "LOCKED").containsEntry("lock_token", TOKEN);
    }
    private void installFailureTrigger(String table, String trigger, String when) {
        jdbc.execute("CREATE OR REPLACE FUNCTION " + trigger + "_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced test failure'; END $$");
        String condition = when == null ? "" : " WHEN (" + when + ")";
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT OR UPDATE ON " + table
                + " FOR EACH ROW" + condition + " EXECUTE FUNCTION " + trigger + "_fn()");
    }
    private void dropFailureTrigger(String table, String trigger) {
        jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON " + table);
        jdbc.execute("DROP FUNCTION IF EXISTS " + trigger + "_fn()");
    }
}
