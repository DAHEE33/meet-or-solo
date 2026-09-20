package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
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
    private static final OffsetDateTime WINDOW_STARTED_AT =
            OffsetDateTime.of(2026, 7, 17, 14, 59, 40, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime WINDOW_ENDS_AT =
            OffsetDateTime.of(2026, 7, 17, 14, 59, 50, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime STYLE_CREATED_AT =
            OffsetDateTime.of(2026, 7, 17, 14, 0, 0, 0, ZoneOffset.ofHours(9));
    /** 5인 시나리오의 첫 신청 시각. 이후 후보는 10초 간격으로 들어온다. */
    private static final OffsetDateTime ARRIVAL_BASE =
            OffsetDateTime.of(2026, 7, 17, 14, 59, 10, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime SEARCH_EXPIRES_AT =
            OffsetDateTime.of(2026, 7, 17, 15, 1, 0, 0, ZoneOffset.ofHours(9));
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Autowired MatchingOrchestrationService service;
    @Autowired JdbcTemplate jdbc;

    /**
     * SQL로 직접 넣은 pool을 <b>이미 수집이 끝난 구간</b>에 넣는다.
     *
     * <p>수집 구간은 신청 경로에서 만들어진다. fixture로 pool만 넣으면 구간이 없어 tick이 평가하지
     * 못하고, tick이 구간을 새로 열면 수집 시간이 남아 있어 역시 평가되지 않는다. 이 테스트의
     * 관심사는 조합·점수 저장이므로 수집은 끝난 상태에서 시작한다.
     */
    @org.junit.jupiter.api.BeforeEach
    void stampEndedCollectionWindow() {
        jdbc.update("DELETE FROM match_collection_windows");
        jdbc.update("INSERT INTO match_collection_windows"
                + "(festival_id,started_at,ends_at,status,created_at,updated_at) "
                + "SELECT DISTINCT festival_id, ?, ?, 'COLLECTED', ?, ? FROM match_pools",
                WINDOW_STARTED_AT, WINDOW_ENDS_AT, WINDOW_STARTED_AT, WINDOW_STARTED_AT);
        jdbc.update("UPDATE match_pools p SET collect_window_id = "
                + "(SELECT w.id FROM match_collection_windows w WHERE w.festival_id = p.festival_id)");
    }

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
     * 같은 배치에 유효 후보 5명이 있을 때 <b>신청 순서가 아니라 궁합 점수로</b> 조합이 선택되는지
     * 검증한다. 이번 변경(즉시 조합 경로 제거)의 회귀 방지선이다.
     *
     * <p>바로 위 테스트와 목적이 다르다. 그쪽은 후보가 정확히 4명이라 조합이 하나뿐이고
     * "점수 분해값이 컬럼까지 도달하는가"를 본다. 여기서는 <b>조합이 여러 개일 때 무엇이
     * 선택되는가</b>를 본다. 즉시 조합 경로가 살아 있으면 후보가 2명을 넘지 못해 이 상황 자체가
     * 만들어지지 않았다.
     *
     * <p>태그는 fixture가 전원 {@code PHOTO} 하나로 주므로 모든 pair의 Jaccard가 100으로
     * 같다. 따라서 총점 순위는 코사인만으로 갈린다. 희망 인원을 전원 2명으로 두는 것이 전제다.
     * 3인 이상을 허용하면 조합 우선순위가 점수보다 인원 수를 먼저 보므로 점수 검증이 되지 않는다.
     *
     * <p>신청 순서는 1 → 2 → 6 → 10 → 11이다. 순서대로 묶으면 (1,2)·(6,10)이고 11이 남는다.
     * 점수대로 묶으면 (1,10)·(2,11)이고 6이 남는다. 둘이 겹치지 않으므로 결과만 보고
     * 어느 쪽으로 동작했는지 구분할 수 있다.
     */
    @Test void 같은_배치의_후보_5명_중_신청순서가_아니라_임베딩_점수로_두_조합이_선택된다() {
        prepareFiveCandidateScenario(false);

        MatchingOrchestrationResult result = service.runTick();

        assertThat(result.createdAttemptIds()).as("result=%s", result).hasSize(2);
        assertThat(memberPairs(result.createdAttemptIds()))
                .as("신청 순서대로면 [[1,2],[6,10]]이 된다")
                .containsExactly(List.of(9_110_001L, 9_110_010L), List.of(9_110_002L, 9_110_011L));
        // 남은 한 명은 매칭되지 않고 대기 상태로 돌아온다.
        assertThat(poolStatus(9_110_006L)).isEqualTo("WAITING");
        result.createdAttemptIds().forEach(attemptId ->
                assertThat(createdBy(attemptId)).isEqualTo("SCHEDULER"));
        // 태그가 상수였고 임베딩이 실제로 적용됐다는 것까지 확인해야 점수 검증이 성립한다.
        assertAllPairsUsedEmbedding(result.createdAttemptIds());
    }

    /**
     * 임베딩만 맞바꾸면 선택되는 조합도 바뀌는지 확인한다.
     *
     * <p>위 테스트만으로는 "우연히 그 조합이 나왔다"를 배제하지 못한다. 회원·신청 순서·태그를
     * 그대로 둔 채 9110010과 9110011의 벡터만 교환하면 기대 조합이 (1,11)·(2,10)으로 바뀐다.
     * 선택 결과가 실제로 임베딩에 의존한다는 뜻이다.
     */
    @Test void 임베딩을_맞바꾸면_선택되는_조합도_바뀐다() {
        prepareFiveCandidateScenario(true);

        MatchingOrchestrationResult result = service.runTick();

        assertThat(result.createdAttemptIds()).as("result=%s", result).hasSize(2);
        assertThat(memberPairs(result.createdAttemptIds()))
                .containsExactly(List.of(9_110_001L, 9_110_011L), List.of(9_110_002L, 9_110_010L));
        assertThat(poolStatus(9_110_006L)).isEqualTo("WAITING");
        assertAllPairsUsedEmbedding(result.createdAttemptIds());
    }

    /**
     * 유효 후보를 5명으로 만든다. 희망 인원 2명, 태그 동일(fixture의 {@code PHOTO}), 차단·쿨타임·
     * 재매칭 제외 없음, 전원 COMPLETED 임베딩 보유.
     *
     * <p>벡터는 앞 두 성분만 쓰는 단위 벡터이고 각도로 설계했다. 0°(1) / 90°(2) / 45°(6) /
     * 2°(10) / 85°(11)이므로 코사인 1위는 1-10(cos 2°), 2위는 2-11(cos 5°)이고 6은 어느 쪽과도
     * 그보다 가깝지 않다. 외부 임베딩 API는 호출하지 않는다.
     *
     * @param swapEmbeddings 9110010과 9110011의 벡터를 맞바꿀지 여부
     */
    private void prepareFiveCandidateScenario(boolean swapEmbeddings) {
        jdbc.update("DELETE FROM user_blocks");
        jdbc.update("DELETE FROM match_cooldowns");
        jdbc.update("DELETE FROM match_opponent_exclusions");
        jdbc.update("DELETE FROM member_preference_embeddings");
        jdbc.update("UPDATE match_pools SET status='PROPOSED' "
                + "WHERE member_id NOT IN (9110001,9110002,9110006,9110010,9110011)");

        // 신청 순서를 10초 간격으로 분명히 해 둔다. 점수 순위와 겹치지 않게 배치했다.
        long[] arrivalOrder = {9_110_001L, 9_110_002L, 9_110_006L, 9_110_010L, 9_110_011L};
        for (int index = 0; index < arrivalOrder.length; index++) {
            jdbc.update("UPDATE match_pools SET status='WAITING', preferred_group_size=2, "
                    + "allow_minimum_two=TRUE, entered_at=?, search_expires_at=? WHERE member_id=?",
                    ARRIVAL_BASE.plusSeconds(10L * index), SEARCH_EXPIRES_AT, arrivalOrder[index]);
        }

        String angle2 = vectorLiteral("0.99939", "0.03490");
        String angle85 = vectorLiteral("0.08716", "0.99619");
        insertEmbedding(9_110_001L, vectorLiteral("1", "0"), "COMPLETED");
        insertEmbedding(9_110_002L, vectorLiteral("0", "1"), "COMPLETED");
        insertEmbedding(9_110_006L, vectorLiteral("0.70711", "0.70711"), "COMPLETED");
        insertEmbedding(9_110_010L, swapEmbeddings ? angle85 : angle2, "COMPLETED");
        insertEmbedding(9_110_011L, swapEmbeddings ? angle2 : angle85, "COMPLETED");
    }

    /** attempt별 회원 쌍. 첫 회원 id 순으로 정렬해 비교를 결정적으로 만든다. */
    private List<List<Long>> memberPairs(List<Long> attemptIds) {
        return attemptIds.stream()
                .map(attemptId -> jdbc.queryForList(
                        "SELECT member_id FROM match_attempt_members WHERE attempt_id=? ORDER BY member_id",
                        Long.class, attemptId))
                .sorted(Comparator.comparing(members -> members.get(0)))
                .toList();
    }

    private String poolStatus(long memberId) {
        return jdbc.queryForObject("SELECT status FROM match_pools WHERE member_id=?", String.class, memberId);
    }

    private String createdBy(long attemptId) {
        return jdbc.queryForObject("SELECT created_by FROM match_attempts WHERE id=?", String.class, attemptId);
    }

    /** 태그가 상수(Jaccard 100)였고 임베딩이 실제 적용됐는지. 둘 중 하나라도 어긋나면 점수 검증이 아니다. */
    private void assertAllPairsUsedEmbedding(List<Long> attemptIds) {
        attemptIds.forEach(attemptId -> jdbc.queryForList(
                "SELECT member_id,jaccard_score,embedding_applied FROM match_attempt_members WHERE attempt_id=?",
                attemptId).forEach(row -> {
                    String context = "attempt " + attemptId + " member " + row.get("member_id");
                    assertThat((BigDecimal) row.get("jaccard_score")).as(context).isEqualByComparingTo("100.00");
                    assertThat(row.get("embedding_applied")).as(context).isEqualTo(true);
                }));
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
        stampEndedCollectionWindow();
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
