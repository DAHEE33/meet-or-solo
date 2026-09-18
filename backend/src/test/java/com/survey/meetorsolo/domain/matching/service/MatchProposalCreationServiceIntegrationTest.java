package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.survey.meetorsolo.domain.matching.group.MatchGroupCombination;
import com.survey.meetorsolo.domain.matching.group.MatchingCandidate;
import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import com.survey.meetorsolo.domain.safety.block.service.MatchBlockService;
import com.survey.meetorsolo.domain.safety.block.service.MemberBlockService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
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

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        // 점수 분해 기대값이 환경변수 MATCHING_SCORING_* 에 흔들리지 않도록 고정한다.
        "app.matching.scoring.jaccard-weight=0.70",
        "app.matching.scoring.embedding-weight=0.30"
}) @Testcontainers
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchProposalCreationServiceIntegrationTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026,7,17,15,0,0,0, ZoneOffset.ofHours(9));
    private static final String TOKEN = "create-token";
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Autowired MatchProposalCreationService service; @Autowired JdbcTemplate jdbc;
    @Autowired MatchOpponentExclusionService opponentExclusions;
    @Autowired MatchBlockService blockService;
    @Autowired MemberBlockService memberBlockService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;

    @ParameterizedTest @ValueSource(ints = {2,3,4})
    void 정확한_인원으로_attempt_member_proposal과_PROPOSED_pool을_원자_생성한다(int size) {
        MatchGroupCombination group = prepareGroup(size);
        long attemptId = service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)).attemptId();
        assertThat(jdbc.queryForMap("SELECT status,score,started_at,expires_at FROM match_attempts WHERE id=?", attemptId))
                .containsEntry("status", "WAITING_RESPONSES").containsEntry("score", new BigDecimal("100.00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_attempt_members WHERE attempt_id=? AND status='PROPOSED' AND member_score=100.00", Integer.class, attemptId)).isEqualTo(size);
        // 전원 동일 태그 + 임베딩 미보유이므로 Jaccard 단독이고 분해값도 그 사실을 그대로 남긴다.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_attempt_members WHERE attempt_id=? AND jaccard_score=100.00 AND cosine_score IS NULL AND embedding_applied=FALSE AND embedding_pair_count=0", Integer.class, attemptId)).isEqualTo(size);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_proposals WHERE attempt_id=? AND proposal_type='INITIAL_MATCH' AND proposal_round=1 AND status='SENT' AND sent_at=? AND expires_at=?", Integer.class, attemptId, NOW, NOW.plusSeconds(30))).isEqualTo(size);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE id IN (" + ids(size) + ") AND status='PROPOSED' AND locked_at IS NULL AND lock_token IS NULL", Integer.class)).isEqualTo(size);
    }

    /**
     * 점수 분해 저장의 핵심 검증.
     *
     * <p>후보 태그와 임베딩 보유 여부를 인원수별로 다르게 구성해 2인 fallback, 3인 혼합, 4인 전원 보유를
     * 덮는다. 3인 혼합은 한 회원 안에서 임베딩 pair와 fallback pair가 섞이는 유일한 구간이라 이 작업의
     * 설계 결정이 걸린 곳이고, 아래 세 개의 focused 테스트로 pair_count 0/1/2를 모두 지난다.
     */
    @ParameterizedTest @ValueSource(ints = {2,3,4})
    void 점수_분해를_pair_구성별로_저장한다(int size) {
        Scenario scenario = switch (size) {
            case 2 -> TWO_FALLBACK;
            case 3 -> THREE_MIXED;
            default -> FOUR_ALL_EMBEDDED;
        };
        assertScenario(scenario);
    }

    @Test void 세명_전원_임베딩_보유는_모든_pair에_임베딩이_적용된다() {
        assertScenario(THREE_ALL_EMBEDDED);
    }

    @Test void 세명_전원_임베딩_미보유는_모든_회원의_cosine이_null이다() {
        assertScenario(THREE_NONE_EMBEDDED);
    }

    @Test void 세명중_한명만_보유하면_어느_pair에도_적용되지_않아_전원_미보유와_같은_분해가_저장된다() {
        // score()는 짝 단위 계산이라 양쪽 모두 보유해야 코사인 항이 작동한다. 혼자 입력한 회원도
        // 상대가 없으면 태그 계산과 같아진다는 사실이 저장 값으로 드러나는지 확인한다.
        assertScenario(THREE_ONLY_ONE_EMBEDDED);
        assertThat(THREE_ONLY_ONE_EMBEDDED.expectations()).isEqualTo(THREE_NONE_EMBEDDED.expectations());
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

    @Test void 현재_checkin_pair_exclusion을_proposal_생성_직전에_재검증한다() {
        MatchGroupCombination group = prepareGroup(2);
        long sourceProposal = insertExclusionSource();
        jdbc.update("""
                INSERT INTO match_opponent_exclusions(
                    lower_member_id,higher_member_id,lower_checkin_id,higher_checkin_id,
                    rejected_by_member_id,source_proposal_id,created_at
                ) VALUES (9110002,9110006,9120002,9120006,9110002,?,?)
                """, sourceProposal, NOW);

        assertThatThrownBy(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)))
                .isInstanceOf(MatchProposalCreationException.class).hasMessageContaining("제외");
        assertThat(createdAttemptCount()).isZero();
        assertLocked(9_120_002L);
        assertLocked(9_120_006L);
    }

    @Test void exclusion_commit과_proposal_생성_race는_pair_advisory_lock뒤_재조회로_생성을_차단한다() throws Exception {
        MatchGroupCombination group = prepareGroup(2);
        long sourceProposal = insertExclusionSource();
        MatchOpponentPair pair = MatchOpponentPair.of(9110002L,9120002L,9110006L,9120006L);
        CountDownLatch exclusionInserted = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var rejection = workers.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                opponentExclusions.lockPairs(List.of(pair));
                jdbc.update("""
                        INSERT INTO match_opponent_exclusions(
                            lower_member_id,higher_member_id,lower_checkin_id,higher_checkin_id,
                            rejected_by_member_id,source_proposal_id,created_at
                        ) VALUES (9110002,9110006,9120002,9120006,9110002,?,?)
                        """, sourceProposal, NOW);
                exclusionInserted.countDown();
                try {
                    if (!allowCommit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("commit 대기 timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(exclusionInserted.await(10, TimeUnit.SECONDS)).isTrue();
            var proposalCreation = workers.submit(() ->
                    service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)));
            allowCommit.countDown();
            rejection.get(10, TimeUnit.SECONDS);

            assertThatThrownBy(() -> proposalCreation.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(MatchProposalCreationException.class);
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
        }
        assertThat(createdAttemptCount()).isZero();
    }

    @Test void block_commit과_proposal_생성_race는_member_pair_lock뒤_재조회로_생성을_차단한다() throws Exception {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("""
                INSERT INTO match_groups(
                    id,attempt_id,festival_id,status,confirmed_member_count,confirmed_at,created_at,updated_at
                ) VALUES (9170099,9130001,9100001,'IN_PROGRESS',2,?,?,?)
                """, NOW, NOW, NOW);
        jdbc.update("""
                INSERT INTO match_group_members(
                    id,group_id,member_id,status,allow_minimum_two,created_at,updated_at
                ) VALUES (9180098,9170099,9110002,'JOINED',true,?,?),
                         (9180099,9170099,9110006,'JOINED',true,?,?)
                """, NOW, NOW, NOW, NOW);
        CountDownLatch blockInserted = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var blocking = workers.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        blockService.block(9110002L, 9170099L, 9110006L);
                        blockInserted.countDown();
                        try {
                            if (!allowCommit.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("block commit 대기 timeout");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }));
            assertThat(blockInserted.await(10, TimeUnit.SECONDS)).isTrue();
            var proposalCreation = workers.submit(() ->
                    service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)));
            allowCommit.countDown();
            blocking.get(10, TimeUnit.SECONDS);

            assertThatThrownBy(() -> proposalCreation.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(MatchProposalCreationException.class);
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
        }
        assertThat(createdAttemptCount()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM user_blocks
                WHERE blocker_member_id=9110002 AND blocked_member_id=9110006
                """, Integer.class)).isOne();
    }

    @Test void proposal_생성_직전_해제되면_최종_검증에서_해제_상태를_반영한다() {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("INSERT INTO user_blocks(blocker_member_id,blocked_member_id,reason) VALUES (9110002,9110006,'TEST')");
        memberBlockService.unblock(9_110_002L, 9_110_006L);

        service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30));

        assertThat(createdAttemptCount()).isOne();
    }

    @Test void 해제_선행_race는_commit후_proposal이_차단없는_상태를_관찰한다() throws Exception {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("INSERT INTO user_blocks(blocker_member_id,blocked_member_id,reason) VALUES (9110002,9110006,'TEST')");
        CountDownLatch deletedBeforeCommit = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var unblock = workers.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                memberBlockService.unblock(9_110_002L, 9_110_006L);
                deletedBeforeCommit.countDown();
                try {
                    if (!allowCommit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("commit 대기 timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(deletedBeforeCommit.await(10, TimeUnit.SECONDS)).isTrue();
            var proposal = workers.submit(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)));
            allowCommit.countDown();
            unblock.get(10, TimeUnit.SECONDS);
            assertThat(proposal.get(10, TimeUnit.SECONDS).attemptId()).isPositive();
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
        }
        assertThat(createdAttemptCount()).isOne();
    }

    @Test void proposal_선행_race는_transaction_종료후_해제하고_진행중_proposal을_변경하지_않는다() throws Exception {
        MatchGroupCombination group = prepareGroup(2);
        jdbc.update("INSERT INTO user_blocks(blocker_member_id,blocked_member_id,reason) VALUES (9110002,9110006,'TEST')");
        var workers = Executors.newFixedThreadPool(2);
        try (var gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            gate.createStatement().execute("LOCK TABLE user_blocks IN ACCESS EXCLUSIVE MODE");
            var proposal = workers.submit(() -> service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)));
            awaitProposalBlockRead();
            var unblock = workers.submit(() -> memberBlockService.unblock(9_110_002L, 9_110_006L));
            gate.commit();

            assertThatThrownBy(() -> proposal.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(MatchProposalCreationException.class);
            unblock.get(10, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
        assertThat(createdAttemptCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_blocks WHERE blocker_member_id=9110002 AND blocked_member_id=9110006", Integer.class)).isZero();
    }

    private void awaitProposalBlockRead() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM pg_stat_activity
                        WHERE pid <> pg_backend_pid()
                          AND wait_event_type = 'Lock'
                          AND query LIKE '%count(*) FROM user_blocks%'
                    )
                    """, Boolean.class);
            if (Boolean.TRUE.equals(waiting)) return;
            Thread.onSpinWait();
        }
        throw new IllegalStateException("proposal 차단 재조회 대기 timeout");
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
        MatchingCandidate missing = new MatchingCandidate(99_999_999L, 9_110_006L, 9_120_006L, 9_100_001L, 2,
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

    // ---- 점수 분해 검증용 후보 구성과 기대값 ----
    // 벡터는 OpenAI 호출 없이 코사인이 딱 떨어지도록 잡은 임의 단위 벡터다.
    private static final float[] V_X = {1.0f, 0.0f};          // 상호 코사인: V_X-V_DIAG 0.60
    private static final float[] V_DIAG = {0.6f, 0.8f};       //             V_X-V_FLAT 0.80
    private static final float[] V_FLAT = {0.8f, 0.6f};       //             V_DIAG-V_FLAT 0.96
    private static final float[] V_Y = {0.0f, 1.0f};          //             V_X-V_Y 0.00

    private static final List<TravelStyleCode> T_PHOTO = List.of(TravelStyleCode.PHOTO);
    private static final List<TravelStyleCode> T_PHOTO_FOOD = List.of(TravelStyleCode.PHOTO, TravelStyleCode.FOOD);
    private static final List<TravelStyleCode> T_FOOD_ACTIVE = List.of(TravelStyleCode.FOOD, TravelStyleCode.ACTIVE);

    /** 2인 fallback: 한쪽만 임베딩을 가져 코사인 항이 작동하지 않는다. */
    private static final Scenario TWO_FALLBACK = new Scenario(
            List.of(new CandidateProfile(T_PHOTO_FOOD, V_X), new CandidateProfile(T_FOOD_ACTIVE, null)),
            "33.33",
            List.of(new ScoreExpectation(9_110_002L, "33.33", "33.33", null, 0),
                    new ScoreExpectation(9_110_006L, "33.33", "33.33", null, 0)));

    /** 3인 혼합: 회원 2·6은 pair 하나만 임베딩, 회원 10은 벡터가 없어 전부 fallback. */
    private static final Scenario THREE_MIXED = new Scenario(
            List.of(new CandidateProfile(T_PHOTO, V_X), new CandidateProfile(T_PHOTO_FOOD, V_DIAG),
                    new CandidateProfile(T_FOOD_ACTIVE, null)),
            "28.78",
            List.of(new ScoreExpectation(9_110_002L, "26.50", "25.00", "30.00", 1),
                    new ScoreExpectation(9_110_006L, "43.17", "41.67", "46.67", 1),
                    new ScoreExpectation(9_110_010L, "16.67", "16.67", null, 0)));

    private static final Scenario THREE_ALL_EMBEDDED = new Scenario(
            List.of(new CandidateProfile(T_PHOTO, V_X), new CandidateProfile(T_PHOTO_FOOD, V_DIAG),
                    new CandidateProfile(T_FOOD_ACTIVE, V_FLAT)),
            "43.04",
            List.of(new ScoreExpectation(9_110_002L, "38.50", "25.00", "70.00", 2),
                    new ScoreExpectation(9_110_006L, "52.57", "41.67", "78.00", 2),
                    new ScoreExpectation(9_110_010L, "38.07", "16.67", "88.00", 2)));

    private static final List<ScoreExpectation> THREE_TAGS_ONLY = List.of(
            new ScoreExpectation(9_110_002L, "25.00", "25.00", null, 0),
            new ScoreExpectation(9_110_006L, "41.67", "41.67", null, 0),
            new ScoreExpectation(9_110_010L, "16.67", "16.67", null, 0));

    private static final Scenario THREE_NONE_EMBEDDED = new Scenario(
            List.of(new CandidateProfile(T_PHOTO, null), new CandidateProfile(T_PHOTO_FOOD, null),
                    new CandidateProfile(T_FOOD_ACTIVE, null)),
            "27.78", THREE_TAGS_ONLY);

    private static final Scenario THREE_ONLY_ONE_EMBEDDED = new Scenario(
            List.of(new CandidateProfile(T_PHOTO, V_X), new CandidateProfile(T_PHOTO_FOOD, null),
                    new CandidateProfile(T_FOOD_ACTIVE, null)),
            "27.78", THREE_TAGS_ONLY);

    private static final Scenario FOUR_ALL_EMBEDDED = new Scenario(
            List.of(new CandidateProfile(T_PHOTO, V_X), new CandidateProfile(T_PHOTO_FOOD, V_DIAG),
                    new CandidateProfile(T_FOOD_ACTIVE, V_FLAT), new CandidateProfile(T_PHOTO_FOOD, V_Y)),
            "49.91",
            List.of(new ScoreExpectation(9_110_002L, "37.33", "33.33", "46.67", 3),
                    new ScoreExpectation(9_110_006L, "66.38", "61.11", "78.67", 3),
                    new ScoreExpectation(9_110_010L, "39.15", "22.22", "78.67", 3),
                    new ScoreExpectation(9_110_011L, "56.78", "61.11", "46.67", 3)));

    private record CandidateProfile(List<TravelStyleCode> tags, float[] embedding) { }

    private record ScoreExpectation(long memberId, String memberScore, String jaccard, String cosine,
                                    int embeddingPairCount) { }

    private record Scenario(List<CandidateProfile> profiles, String groupScore,
                            List<ScoreExpectation> expectations) { }

    private void assertScenario(Scenario scenario) {
        MatchGroupCombination group = prepareGroup(scenario);
        long attemptId = service.createInitial(group, TOKEN, NOW, Duration.ofSeconds(30)).attemptId();
        assertThat(jdbc.queryForObject("SELECT score FROM match_attempts WHERE id=?", BigDecimal.class, attemptId))
                .isEqualByComparingTo(scenario.groupScore());
        scenario.expectations().forEach(expectation -> assertBreakdown(attemptId, expectation));
    }

    private void assertBreakdown(long attemptId, ScoreExpectation expectation) {
        var row = jdbc.queryForMap("SELECT member_score,jaccard_score,cosine_score,embedding_applied,"
                + "embedding_pair_count FROM match_attempt_members WHERE attempt_id=? AND member_id=?",
                attemptId, expectation.memberId());
        String context = "member " + expectation.memberId();
        assertThat((BigDecimal) row.get("member_score")).as(context)
                .isEqualByComparingTo(expectation.memberScore());
        assertThat((BigDecimal) row.get("jaccard_score")).as(context)
                .isEqualByComparingTo(expectation.jaccard());
        assertThat(((Number) row.get("embedding_pair_count")).intValue()).as(context)
                .isEqualTo(expectation.embeddingPairCount());
        BigDecimal cosine = (BigDecimal) row.get("cosine_score");
        if (expectation.cosine() == null) {
            assertThat(cosine).as(context).isNull();
            assertThat(row.get("embedding_applied")).as(context).isEqualTo(false);
        } else {
            assertThat(cosine).as(context).isEqualByComparingTo(expectation.cosine());
            assertThat(row.get("embedding_applied")).as(context).isEqualTo(true);
        }
        assertReconstructable(context, (BigDecimal) row.get("member_score"),
                (BigDecimal) row.get("jaccard_score"), cosine);
    }

    /**
     * 저장된 분해값과 가중치만으로 member_score를 재구성할 수 있는지 확인한다. 총점 하나만 남기던
     * 구조에서 이 재구성이 불가능했던 것이 점수 분해 저장의 동기다.
     *
     * <p>pair 점수가 pair마다 반올림된 뒤 평균되므로 3~4인은 0.01까지 어긋날 수 있다. 기존
     * member_score 계산 순서를 바꾸지 않는 한 제거할 수 없는 오차라 허용 범위로 둔다.
     */
    private void assertReconstructable(String context, BigDecimal memberScore, BigDecimal jaccard,
                                       BigDecimal cosine) {
        if (cosine == null) {
            assertThat(memberScore).as(context + " fallback 재구성").isEqualByComparingTo(jaccard);
            return;
        }
        BigDecimal reconstructed = jaccard.multiply(new BigDecimal("0.70"))
                .add(cosine.multiply(new BigDecimal("0.30")))
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(reconstructed).as(context + " 가중 합산 재구성")
                .isCloseTo(memberScore, within(new BigDecimal("0.01")));
    }

    private MatchGroupCombination prepareGroup(int size) {
        return prepareGroup(size, IntStream.range(0, size)
                        .mapToObj(index -> new CandidateProfile(List.of(TravelStyleCode.PHOTO), null)).toList(),
                new BigDecimal("100.00"));
    }

    private MatchGroupCombination prepareGroup(Scenario scenario) {
        return prepareGroup(scenario.profiles().size(), scenario.profiles(),
                new BigDecimal(scenario.groupScore()));
    }

    private MatchGroupCombination prepareGroup(int size, List<CandidateProfile> profiles, BigDecimal groupScore) {
        jdbc.update("DELETE FROM user_blocks"); jdbc.update("DELETE FROM match_cooldowns");
        List<Long> poolIds = List.of(9_120_002L, 9_120_006L, 9_120_010L, 9_120_011L).subList(0, size);
        for (long id : poolIds) jdbc.update("UPDATE match_pools SET preferred_group_size=?,status='LOCKED',locked_at=?,lock_token=?,search_expires_at=? WHERE id=?", size, NOW, TOKEN, NOW.plusMinutes(1), id);
        List<MatchingCandidate> candidates = IntStream.range(0, poolIds.size()).mapToObj(index -> {
            long id = poolIds.get(index);
            CandidateProfile profile = profiles.get(index);
            return new MatchingCandidate(id, 9_110_000L + (id - 9_120_000L), id, 9_100_001L,
                    size, false, NOW.minusSeconds(index), profile.tags(), profile.embedding());
        }).toList();
        return new MatchGroupCombination(candidates, groupScore);
    }
    private long insertExclusionSource() {
        Long attempt = jdbc.queryForObject("""
                INSERT INTO match_attempts(
                    festival_id,target_group_size,status,score,created_by,started_at,expires_at,created_at,updated_at
                ) VALUES (9100001,2,'FAILED',0,'SCHEDULER',?,?,?,?) RETURNING id
                """, Long.class, NOW.minusMinutes(1), NOW.plusMinutes(1), NOW.minusMinutes(1), NOW);
        return jdbc.queryForObject("""
                INSERT INTO match_proposals(
                    attempt_id,member_id,proposal_type,proposal_round,status,sent_at,expires_at,created_at,updated_at
                ) VALUES (?,9110002,'INITIAL_MATCH',1,'REJECTED',?,?,?,?) RETURNING id
                """, Long.class, attempt, NOW.minusSeconds(30), NOW.plusSeconds(30), NOW.minusSeconds(30), NOW);
    }
    private String ids(int size) { return List.of("9120002", "9120006", "9120010", "9120011").subList(0, size).stream().collect(java.util.stream.Collectors.joining(",")); }

    private MatchingCandidate candidate(MatchingCandidate source, long memberId, long festivalId, int preferredSize) {
        return new MatchingCandidate(source.poolId(), memberId, source.checkinId(), festivalId, preferredSize, source.allowMinimumTwo(),
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
