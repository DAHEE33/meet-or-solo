package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 수집 구간이 실제로 후보를 모았다가 한 번에 평가하는지 검증한다.
 *
 * <p>여기서 시간을 직접 진행시키는 것이 핵심이다. 5명을 미리 넣고 tick을 한 번 부르는 방식으로는
 * "수집 도중에는 매칭되지 않는다"를 확인할 수 없다. 신청 사이사이에 tick을 여러 번 돌려서
 * <b>조기 제안이 없다는 것을 먼저</b> 보이고, 수집이 끝난 뒤에야 함께 평가되는지 본다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        "app.matching.scoring.jaccard-weight=0.70",
        "app.matching.scoring.embedding-weight=0.30",
        "app.matching.scheduler.collect-window=10s",
        "app.matching.scheduler.matching-fixed-delay=2s"
})
@Testcontainers
@Import(MatchingCollectionWindowIntegrationTest.MutableClockConfiguration.class)
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchingCollectionWindowIntegrationTest {

    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final long FESTIVAL = 9_100_001L;
    /** 신청 순서. 점수 순위와 겹치지 않게 배치했다. */
    private static final long FIRST = 9_110_001L;
    private static final long SECOND = 9_110_002L;
    private static final long THIRD = 9_110_006L;
    private static final long FOURTH = 9_110_010L;
    private static final long FIFTH = 9_110_011L;
    private static final OffsetDateTime BASE_TIME =
            OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));

    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired MatchingOrchestrationService orchestration;
    @Autowired MatchPoolEntryService entryService;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetScenario() {
        clock.reset(BASE_TIME.toInstant());
        // fixture가 넣어둔 pool·차단·쿨타임을 걷어내고 신청 경로로 직접 들어가게 한다.
        // 수집 구간은 신청 시점에 만들어지므로 SQL로 미리 넣은 pool로는 검증할 수 없다.
        jdbc.update("DELETE FROM match_attempt_members");
        jdbc.update("DELETE FROM match_proposals");
        jdbc.update("DELETE FROM match_attempts");
        jdbc.update("DELETE FROM match_pools");
        jdbc.update("DELETE FROM match_collection_windows");
        jdbc.update("DELETE FROM user_blocks");
        jdbc.update("DELETE FROM match_cooldowns");
        jdbc.update("DELETE FROM match_opponent_exclusions");
        jdbc.update("DELETE FROM member_preference_embeddings");
    }

    /**
     * 최우선 완료 조건.
     *
     * <p>A~E가 첫 신청부터 10초 안에 시간차로 들어오고 그 사이 tick이 여러 번 돌아도 조기 매칭이
     * 없어야 하며, 수집이 끝난 뒤 다섯 명을 함께 비교해야 한다.
     *
     * <p>신청 순서는 1 → 2 → 6 → 10 → 11이다. 순서대로 묶이면 (1,2)·(6,10)이고 11이 남는다.
     * 점수대로 묶이면 (1,10)·(2,11)이고 6이 남는다. 두 결과가 겹치지 않으므로 어느 쪽으로
     * 동작했는지 결과만 보고 구분할 수 있다.
     */
    @Test
    void 첫_신청부터_10초_안에_시간차로_들어온_5명이_조기매칭_없이_수집_종료_후_함께_비교된다() {
        prepareEmbeddings(false);

        enter(FIRST);                       // t=0  수집 구간 시작
        assertNoProposalYet("t=0");

        advanceSeconds(2);
        enter(SECOND);                      // t=2
        assertNoProposalYet("t=2");
        advanceSeconds(1);
        assertNoProposalYet("t=3");         // 신청 없이 tick만 돌아도 마찬가지다

        advanceSeconds(1);
        enter(THIRD);                       // t=4
        assertNoProposalYet("t=4");

        advanceSeconds(2);
        enter(FOURTH);                      // t=6
        assertNoProposalYet("t=6");

        advanceSeconds(3);
        enter(FIFTH);                       // t=9
        assertNoProposalYet("t=9");         // 수집 종료 직전까지도 제안이 없어야 한다

        advanceSeconds(2);                  // t=11 수집 종료
        MatchingOrchestrationResult result = orchestration.runTick();

        assertThat(result.createdAttemptIds()).as("result=%s", result).hasSize(2);
        assertThat(memberPairs()).as("신청 순서대로면 [[1,2],[6,10]]이 된다")
                .containsExactly(List.of(FIRST, FOURTH), List.of(SECOND, FIFTH));
        assertThat(poolStatus(THIRD)).isEqualTo("WAITING");
        assertAllPairsUsedEmbedding();
    }

    /** 신청 순서·태그를 그대로 두고 임베딩만 맞바꾸면 선택 결과도 바뀐다. */
    @Test
    void 임베딩을_맞바꾸면_수집_종료_후_선택되는_조합도_바뀐다() {
        prepareEmbeddings(true);

        enter(FIRST);
        advanceSeconds(2);
        enter(SECOND);
        advanceSeconds(2);
        enter(THIRD);
        advanceSeconds(2);
        enter(FOURTH);
        advanceSeconds(3);
        enter(FIFTH);
        assertNoProposalYet("t=9");

        advanceSeconds(2);
        MatchingOrchestrationResult result = orchestration.runTick();

        assertThat(result.createdAttemptIds()).as("result=%s", result).hasSize(2);
        assertThat(memberPairs()).containsExactly(List.of(FIRST, FIFTH), List.of(SECOND, FOURTH));
        assertThat(poolStatus(THIRD)).isEqualTo("WAITING");
    }

    /** 후보가 둘뿐이어도 수집이 끝나면 매칭된다. 출발 조건으로 최소 인원을 두지 않는다. */
    @Test
    void 두_명만_신청해도_수집_종료_후_매칭된다() {
        prepareEmbeddings(false);

        enter(FIRST);
        advanceSeconds(2);
        enter(SECOND);
        assertNoProposalYet("수집 중");

        advanceSeconds(9);
        MatchingOrchestrationResult result = orchestration.runTick();

        assertThat(result.createdAttemptIds()).hasSize(1);
        assertThat(memberPairs()).containsExactly(List.of(FIRST, SECOND));
    }

    /** 수집 종료 후 들어온 신청자는 이번 평가에서 빠지고 다음 구간에서 처리된다. */
    @Test
    void 수집_종료_뒤_신청자는_다음_구간에서_평가된다() {
        prepareEmbeddings(false);

        enter(FIRST);
        advanceSeconds(2);
        enter(SECOND);
        advanceSeconds(9);                  // t=11 수집 종료
        enter(THIRD);                       // 종료 후 신청 → 새 구간

        MatchingOrchestrationResult first = orchestration.runTick();
        assertThat(first.createdAttemptIds()).hasSize(1);
        assertThat(memberPairs()).containsExactly(List.of(FIRST, SECOND));
        assertThat(poolStatus(THIRD)).as("다음 구간을 기다린다").isEqualTo("WAITING");

        // 세 번째 신청이 연 구간이 아직 수집 중일 때 네 번째가 합류해야 둘이 함께 평가된다.
        advanceSeconds(2);
        enter(FOURTH);
        advanceSeconds(9);
        MatchingOrchestrationResult second = orchestration.runTick();

        assertThat(second.createdAttemptIds()).hasSize(1);
        assertThat(memberPairsOf(second.createdAttemptIds())).containsExactly(List.of(THIRD, FOURTH));
    }

    /**
     * 첫 신청자가 취소해도 수집 종료 시각이 움직이지 않는다.
     *
     * <p>남은 후보의 {@code MIN(entered_at)}으로 구간을 다시 계산하면 종료가 뒤로 밀린다.
     */
    @Test
    void 첫_신청자가_취소해도_수집_종료_시각이_움직이지_않는다() {
        prepareEmbeddings(false);

        enter(FIRST);
        long windowId = openWindowId();
        OffsetDateTime endsAt = windowEndsAt(windowId);

        advanceSeconds(2);
        enter(SECOND);
        advanceSeconds(1);
        jdbc.update("UPDATE match_pools SET status='CANCELLED' WHERE member_id=?", FIRST);

        advanceSeconds(3);
        enter(THIRD);

        assertThat(windowEndsAt(windowId)).as("구간 종료 시각은 고정이다").isEqualTo(endsAt);
        assertThat(openWindowId()).as("같은 구간이 유지된다").isEqualTo(windowId);
    }

    private void enter(long memberId) {
        entryService.enter(memberId, new MatchPoolEntryRequest(FESTIVAL, 2, true, List.of()));
    }

    /** tick을 돌리고 제안이 아직 없다는 것을 확인한다. */
    private void assertNoProposalYet(String label) {
        MatchingOrchestrationResult result = orchestration.runTick();
        assertThat(result.createdAttemptIds()).as("%s: 수집 중에는 제안이 없어야 한다", label).isEmpty();
        assertThat(attemptCount()).as("%s: attempt가 생기면 안 된다", label).isZero();
    }

    private void advanceSeconds(long seconds) {
        clock.advance(Duration.ofSeconds(seconds));
    }

    private int attemptCount() {
        return jdbc.queryForObject("SELECT count(*) FROM match_attempts", Integer.class);
    }

    private List<List<Long>> memberPairs() {
        return memberPairsOf(jdbc.queryForList("SELECT id FROM match_attempts ORDER BY id", Long.class));
    }

    private List<List<Long>> memberPairsOf(List<Long> attemptIds) {
        return attemptIds.stream()
                .map(attemptId -> jdbc.queryForList(
                        "SELECT member_id FROM match_attempt_members WHERE attempt_id=? ORDER BY member_id",
                        Long.class, attemptId))
                .sorted(Comparator.comparing(members -> members.get(0)))
                .toList();
    }

    private String poolStatus(long memberId) {
        return jdbc.queryForObject(
                "SELECT status FROM match_pools WHERE member_id=? ORDER BY id DESC LIMIT 1",
                String.class, memberId);
    }

    private long openWindowId() {
        return jdbc.queryForObject(
                "SELECT id FROM match_collection_windows WHERE festival_id=? AND status='OPEN'",
                Long.class, FESTIVAL);
    }

    private OffsetDateTime windowEndsAt(long windowId) {
        return jdbc.queryForObject("SELECT ends_at FROM match_collection_windows WHERE id=?",
                OffsetDateTime.class, windowId);
    }

    /** 태그가 상수였고 임베딩이 실제 적용됐는지. 둘 중 하나라도 어긋나면 점수 검증이 아니다. */
    private void assertAllPairsUsedEmbedding() {
        jdbc.queryForList("SELECT member_id, jaccard_score, embedding_applied FROM match_attempt_members")
                .forEach(row -> {
                    String context = "member " + row.get("member_id");
                    assertThat(row.get("jaccard_score").toString()).as(context).isEqualTo("100.00");
                    assertThat(row.get("embedding_applied")).as(context).isEqualTo(true);
                });
    }

    /**
     * 앞 두 성분만 쓰는 단위 벡터를 각도로 배치한다. 0°(1) / 90°(2) / 45°(6) / 2°(10) / 85°(11)이라
     * 코사인 1위가 1-10, 2위가 2-11이고 6은 어느 쪽과도 그보다 가깝지 않다.
     * 태그는 fixture가 전원 PHOTO 하나로 주므로 모든 pair의 Jaccard가 100으로 같다.
     */
    private void prepareEmbeddings(boolean swapFourthAndFifth) {
        String angle2 = vectorLiteral("0.99939", "0.03490");
        String angle85 = vectorLiteral("0.08716", "0.99619");
        insertEmbedding(FIRST, vectorLiteral("1", "0"));
        insertEmbedding(SECOND, vectorLiteral("0", "1"));
        insertEmbedding(THIRD, vectorLiteral("0.70711", "0.70711"));
        insertEmbedding(FOURTH, swapFourthAndFifth ? angle85 : angle2);
        insertEmbedding(FIFTH, swapFourthAndFifth ? angle2 : angle85);
    }

    private void insertEmbedding(long memberId, String vector) {
        jdbc.update("INSERT INTO member_preference_embeddings("
                        + "member_id,preference_text,embedding,embedding_model,embedding_status,created_at,updated_at) "
                        + "VALUES (?,?,?::vector,?,'COMPLETED',?,?)",
                memberId, "검증용 취향 원문", vector, "text-embedding-3-small", BASE_TIME, BASE_TIME);
    }

    private String vectorLiteral(String first, String second) {
        StringBuilder literal = new StringBuilder(EMBEDDING_DIMENSIONS * 3)
                .append('[').append(first).append(',').append(second);
        for (int index = 2; index < EMBEDDING_DIMENSIONS; index++) {
            literal.append(",0");
        }
        return literal.append(']').toString();
    }

    /** 테스트가 시간을 직접 진행시킨다. 수집 구간은 시간이 지나야 닫히므로 고정 Clock으로는 검증할 수 없다. */
    static class MutableClock extends Clock {
        private final ZoneId zone = ZoneId.of("Asia/Seoul");
        private volatile Instant instant = Instant.parse("2026-07-17T06:00:00Z");

        void reset(Instant value) {
            this.instant = value;
        }

        void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override public ZoneId getZone() {
            return zone;
        }

        @Override public Clock withZone(ZoneId targetZone) {
            return this;
        }

        @Override public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {
        @Bean @Primary MutableClock mutableMatchingClock() {
            return new MutableClock();
        }
    }
}
