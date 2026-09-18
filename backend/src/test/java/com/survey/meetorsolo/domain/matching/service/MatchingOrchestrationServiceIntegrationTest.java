package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        // 점수 분해 기대값이 환경변수 MATCHING_SCORING_* 에 흔들리지 않도록 고정한다.
        "app.matching.scoring.jaccard-weight=0.70",
        "app.matching.scoring.embedding-weight=0.30"
})
@Testcontainers
@Import(MatchingOrchestrationServiceIntegrationTest.FixedInputs.class)
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MatchingOrchestrationServiceIntegrationTest {
    private static final String TOKEN = "orchestration-fixed-token";
    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final OffsetDateTime STYLE_CREATED_AT =
            OffsetDateTime.of(2026, 7, 17, 14, 0, 0, 0, ZoneOffset.ofHours(9));
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Autowired MatchingOrchestrationService service;
    @Autowired JdbcTemplate jdbc;

    @Test void 그룹_생성실패후_token_owned_LOCKED를_즉시_release한다() {
        installMemberFailureTrigger();
        MatchingOrchestrationResult result;
        try { result = service.runTick(); } finally { dropMemberFailureTrigger(); }
        assertThat(result.failedGroupCount()).isOne();
        assertThat(result.createdAttemptIds()).isEmpty();
        assertThat(result.releasedCount()).isEqualTo(result.claimedCount()).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE lock_token=?", Integer.class, TOKEN)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_pools WHERE status='LOCKED'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_attempts WHERE started_at='2026-07-17T15:00:00+09:00'", Integer.class)).isZero();
    }

    /**
     * DB에 저장된 실제 임베딩이 점수 분해 컬럼까지 도달하는지 검증한다.
     *
     * <p>{@code MatchProposalCreationServiceIntegrationTest}는 {@code float[]}를
     * {@code MatchingCandidate}에 직접 주입해 산술과 저장을 검증하므로 아래 구간이 비어 있었다.
     *
     * <pre>
     *   member_preference_embeddings (vector(1536), COMPLETED)
     *     -> MatchingBatchReader.read()
     *     -> MatchingCandidate.preferenceEmbedding
     *     -> match_attempt_members.cosine_score
     * </pre>
     *
     * <p>여기서는 scheduler tick 전체를 태워 pgvector에 실제로 넣은 1536차원 벡터가 컬럼에
     * 반영되는지 확인한다. 회원 9110007은 벡터를 가지고 있지만 {@code FAILED}이므로 reader의
     * {@code COMPLETED} 필터가 동작하면 어느 pair에도 임베딩이 적용되지 않아야 한다.
     */
    @Test void DB에_저장된_임베딩이_scheduler_tick을_거쳐_점수_분해_컬럼에_반영된다() {
        prepareEmbeddingScenario();

        MatchingOrchestrationResult result = service.runTick();

        assertThat(result.createdAttemptIds()).as("result=%s", result).hasSize(1);
        long attemptId = result.createdAttemptIds().get(0);
        assertThat(jdbc.queryForObject("SELECT score FROM match_attempts WHERE id=?", BigDecimal.class, attemptId))
                .isEqualByComparingTo("52.08");
        // 태그와 벡터가 서로 다르므로 각 회원의 분해값도 갈린다. 회원 9110007만 임베딩 미적용이다.
        assertBreakdown(attemptId, 9_110_001L, "42.33", "33.33", "63.33", 2);
        assertBreakdown(attemptId, 9_110_002L, "68.38", "61.11", "85.33", 2);
        assertBreakdown(attemptId, 9_110_006L, "36.49", "22.22", "69.78", 2);
        assertBreakdown(attemptId, 9_110_007L, "61.11", "61.11", null, 0);
    }

    /**
     * 회원 9110001·9110002·9110006·9110007 네 명만 후보로 남기고 태그와 임베딩을 구성한다.
     * 후보가 정확히 4명이므로 희망 인원 4의 조합이 하나뿐이고 그룹 선정 결과가 결정적이다.
     */
    private void prepareEmbeddingScenario() {
        jdbc.update("DELETE FROM user_blocks");
        // fixture는 회원 9110007에게 ACTIVE cooldown을 주고 9110001-9110006 차단을 넣어 둔다.
        // 둘 다 두면 후보가 4명이 되지 않아 그룹이 만들어지지 않는다.
        jdbc.update("DELETE FROM match_cooldowns");
        jdbc.update("UPDATE match_pools SET status='PROPOSED' WHERE member_id NOT IN (9110001,9110002,9110006,9110007)");

        // 태그: 9110001 {PHOTO}, 9110002 {PHOTO,FOOD}, 9110006 {FOOD,ACTIVE}, 9110007 {PHOTO,FOOD}
        jdbc.update("DELETE FROM member_travel_styles WHERE member_id=9110006");
        jdbc.update("INSERT INTO member_travel_styles(member_id,style_code,created_at) VALUES "
                + "(9110002,'FOOD',?),(9110006,'FOOD',?),(9110006,'ACTIVE',?),(9110007,'FOOD',?)",
                STYLE_CREATED_AT, STYLE_CREATED_AT, STYLE_CREATED_AT, STYLE_CREATED_AT);

        // 벡터: 상호 코사인이 딱 떨어지도록 앞 두 성분만 쓰는 1536차원 단위 벡터
        insertEmbedding(9_110_001L, vectorLiteral("1", "0"), "COMPLETED");     // 1-2 0.60, 1-6 0.80
        insertEmbedding(9_110_002L, vectorLiteral("0.6", "0.8"), "COMPLETED"); // 2-6 0.96
        insertEmbedding(9_110_006L, vectorLiteral("0.8", "0.6"), "COMPLETED");
        // COMPLETED가 아니면 벡터가 있어도 reader가 읽지 않아야 한다.
        insertEmbedding(9_110_007L, vectorLiteral("1", "0"), "FAILED");
    }

    private void insertEmbedding(long memberId, String vector, String status) {
        jdbc.update("INSERT INTO member_preference_embeddings("
                + "member_id,preference_text,embedding,embedding_model,embedding_status,created_at,updated_at) "
                + "VALUES (?,?,?::vector,?,?,?,?)",
                memberId, "검증용 취향 원문", vector, "text-embedding-3-small", status,
                STYLE_CREATED_AT, STYLE_CREATED_AT);
    }

    /** 앞 두 성분만 값을 갖고 나머지는 0인 {@code vector(1536)} 리터럴을 만든다. */
    private String vectorLiteral(String first, String second) {
        StringBuilder literal = new StringBuilder(EMBEDDING_DIMENSIONS * 3)
                .append('[').append(first).append(',').append(second);
        for (int index = 2; index < EMBEDDING_DIMENSIONS; index++) {
            literal.append(",0");
        }
        return literal.append(']').toString();
    }

    private void assertBreakdown(long attemptId, long memberId, String memberScore, String jaccard,
                                 String cosine, int embeddingPairCount) {
        var row = jdbc.queryForMap("SELECT member_score,jaccard_score,cosine_score,embedding_applied,"
                + "embedding_pair_count FROM match_attempt_members WHERE attempt_id=? AND member_id=?",
                attemptId, memberId);
        String context = "member " + memberId;
        assertThat((BigDecimal) row.get("member_score")).as(context).isEqualByComparingTo(memberScore);
        assertThat((BigDecimal) row.get("jaccard_score")).as(context).isEqualByComparingTo(jaccard);
        assertThat(((Number) row.get("embedding_pair_count")).intValue()).as(context)
                .isEqualTo(embeddingPairCount);
        if (cosine == null) {
            assertThat(row.get("cosine_score")).as(context).isNull();
            assertThat(row.get("embedding_applied")).as(context).isEqualTo(false);
        } else {
            assertThat((BigDecimal) row.get("cosine_score")).as(context).isEqualByComparingTo(cosine);
            assertThat(row.get("embedding_applied")).as(context).isEqualTo(true);
        }
    }

    private void installMemberFailureTrigger() {
        jdbc.execute("CREATE OR REPLACE FUNCTION test_orchestration_member_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced test failure'; END $$");
        jdbc.execute("CREATE TRIGGER test_orchestration_member BEFORE INSERT ON match_attempt_members FOR EACH ROW EXECUTE FUNCTION test_orchestration_member_fn()");
    }
    private void dropMemberFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_orchestration_member ON match_attempt_members");
        jdbc.execute("DROP FUNCTION IF EXISTS test_orchestration_member_fn()");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedInputs {
        @Bean @Primary Clock fixedMatchingClock() {
            return Clock.fixed(Instant.parse("2026-07-17T06:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
        @Bean @Primary MatchingLockTokenGenerator fixedMatchingTokenGenerator() { return () -> TOKEN; }
    }
}
