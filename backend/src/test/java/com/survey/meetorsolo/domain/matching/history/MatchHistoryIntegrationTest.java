package com.survey.meetorsolo.domain.matching.history;

import static com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.matching.history.dto.MatchHistoryItemResponse;
import com.survey.meetorsolo.domain.matching.history.dto.MatchHistoryMemberResponse;
import com.survey.meetorsolo.domain.matching.history.dto.MatchHistoryResponse;
import com.survey.meetorsolo.domain.matching.history.service.MatchHistoryService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 * docs/19 4.10 만남 종료 후 신고 진입점의 매칭 기록 조회를 실제 PostgreSQL로 검증한다.
 *
 * <p>접수 API가 허용하는 기간(14일)과 목록이 표시하는 신고 가능 여부가 어긋나지 않는지,
 * 취소된 만남과 본인 제외, 이미 신고한 상대 표시, cursor 순서가 맞는지를 본다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=match-history-integration-test-secret",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
@Import(MatchHistoryIntegrationTest.FixedClockConfiguration.class)
@Sql(
        scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class MatchHistoryIntegrationTest {

    private static final long ME = 9_110_001L;
    private static final long PARTNER = 9_110_002L;
    private static final long ANOTHER_PARTNER = 9_110_003L;
    private static final long OUTSIDER = 9_110_004L;
    private static final OffsetDateTime TEST_NOW = NOW.plusSeconds(10);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired MatchHistoryService history;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 종료된_만남과_취소된_만남을_최신순으로_함께_반환한다() {
        insertGroup(9_170_001L, "COMPLETED", TEST_NOW.minusDays(3), null, PARTNER);
        insertGroup(9_170_002L, "CANCELLED", null, TEST_NOW.minusDays(1), ANOTHER_PARTNER);

        MatchHistoryResponse response = history.getMyHistory(ME, null, null);

        assertThat(response.items()).extracting(MatchHistoryItemResponse::groupId)
                .containsExactly(9_170_002L, 9_170_001L);
        assertThat(response.items()).extracting(MatchHistoryItemResponse::status)
                .containsExactly("CANCELLED", "COMPLETED");
        assertThat(response.pagination().hasNext()).isFalse();
        assertThat(response.pagination().nextCursor()).isNull();
    }

    @Test
    void 진행_중인_만남은_기록에_포함하지_않는다() {
        // 활성 참여는 회원당 1건만 가능하므로 같은 그룹의 상태를 바꿔가며 확인한다.
        insertGroup(9_170_001L, "CONFIRMED", null, null, PARTNER);
        assertThat(history.getMyHistory(ME, null, null).items()).isEmpty();

        jdbc.update("UPDATE match_groups SET status = 'IN_PROGRESS' WHERE id = ?", 9_170_001L);
        assertThat(history.getMyHistory(ME, null, null).items()).isEmpty();
    }

    @Test
    void 본인은_신고_대상에서_제외하고_상대만_반환한다() {
        insertGroup(9_170_001L, "COMPLETED", TEST_NOW.minusDays(1), null, PARTNER);

        MatchHistoryResponse response = history.getMyHistory(ME, null, null);

        assertThat(response.items().get(0).members())
                .extracting(MatchHistoryMemberResponse::memberId)
                .containsExactly(PARTNER);
    }

    @Test
    void 참여하지_않은_회원은_그_만남을_조회할_수_없다() {
        insertGroup(9_170_001L, "COMPLETED", TEST_NOW.minusDays(1), null, PARTNER);

        assertThat(history.getMyHistory(OUTSIDER, null, null).items()).isEmpty();
    }

    @Test
    void 신고_가능_여부와_만료_시각은_접수_API와_같은_14일_기준을_쓴다() {
        insertGroup(9_170_001L, "COMPLETED", TEST_NOW.minusDays(14).plusSeconds(1), null, PARTNER);
        insertGroup(9_170_002L, "COMPLETED", TEST_NOW.minusDays(14).minusSeconds(1), null, ANOTHER_PARTNER);

        MatchHistoryResponse response = history.getMyHistory(ME, null, null);

        MatchHistoryItemResponse withinWindow = item(response, 9_170_001L);
        MatchHistoryItemResponse expired = item(response, 9_170_002L);
        assertThat(withinWindow.reportable()).isTrue();
        assertThat(expired.reportable()).isFalse();
        // 기간이 지나도 목록에는 남긴다. 매칭 기록 열람 용도를 겸하기 때문이다.
        assertThat(expired.reportableUntil()).isEqualTo(expired.endedAt().plusDays(14));
    }

    @Test
    void 이미_신고한_상대는_사유와_무관하게_신고됨으로_표시한다() {
        insertGroup(9_170_001L, "COMPLETED", TEST_NOW.minusDays(1), null, PARTNER);
        insertGroup(9_170_002L, "COMPLETED", TEST_NOW.minusDays(2), null, ANOTHER_PARTNER);
        insertReport(ME, PARTNER, 9_170_001L, "RUDE");
        insertReport(ME, PARTNER, 9_170_001L, "SAFETY");

        MatchHistoryResponse response = history.getMyHistory(ME, null, null);

        assertThat(item(response, 9_170_001L).members())
                .singleElement()
                .extracting(MatchHistoryMemberResponse::reported)
                .isEqualTo(true);
        assertThat(item(response, 9_170_002L).members())
                .singleElement()
                .extracting(MatchHistoryMemberResponse::reported)
                .isEqualTo(false);
    }

    @Test
    void 다른_회원이_한_신고는_내_기록에_신고됨으로_보이지_않는다() {
        insertGroup(9_170_001L, "COMPLETED", TEST_NOW.minusDays(1), null, PARTNER);
        insertReport(PARTNER, ME, 9_170_001L, "RUDE");

        assertThat(item(history.getMyHistory(ME, null, null), 9_170_001L).members())
                .singleElement()
                .extracting(MatchHistoryMemberResponse::reported)
                .isEqualTo(false);
    }

    @Test
    void cursor로_다음_page를_이어받고_중복이나_누락이_없다() {
        insertGroup(9_170_001L, "COMPLETED", TEST_NOW.minusDays(3), null, PARTNER);
        insertGroup(9_170_002L, "COMPLETED", TEST_NOW.minusDays(2), null, PARTNER);
        insertGroup(9_170_003L, "COMPLETED", TEST_NOW.minusDays(1), null, PARTNER);

        MatchHistoryResponse first = history.getMyHistory(ME, null, 2);
        assertThat(first.items()).extracting(MatchHistoryItemResponse::groupId)
                .containsExactly(9_170_003L, 9_170_002L);
        assertThat(first.pagination().hasNext()).isTrue();

        MatchHistoryResponse second =
                history.getMyHistory(ME, first.pagination().nextCursor(), 2);
        assertThat(second.items()).extracting(MatchHistoryItemResponse::groupId)
                .containsExactly(9_170_001L);
        assertThat(second.pagination().hasNext()).isFalse();
        assertThat(second.pagination().nextCursor()).isNull();
    }

    @Test
    void 종료_시각이_같아도_id_tiebreaker로_순서가_흔들리지_않는다() {
        OffsetDateTime sameMoment = TEST_NOW.minusDays(1);
        insertGroup(9_170_001L, "COMPLETED", sameMoment, null, PARTNER);
        insertGroup(9_170_002L, "COMPLETED", sameMoment, null, PARTNER);
        insertGroup(9_170_003L, "COMPLETED", sameMoment, null, PARTNER);

        MatchHistoryResponse first = history.getMyHistory(ME, null, 2);
        MatchHistoryResponse second =
                history.getMyHistory(ME, first.pagination().nextCursor(), 2);

        assertThat(first.items()).extracting(MatchHistoryItemResponse::groupId)
                .containsExactly(9_170_003L, 9_170_002L);
        assertThat(second.items()).extracting(MatchHistoryItemResponse::groupId)
                .containsExactly(9_170_001L);
    }

    @Test
    void 위조된_cursor는_거절한다() {
        assertThatThrownBy(() -> history.getMyHistory(ME, "not-a-cursor", null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    private static MatchHistoryItemResponse item(MatchHistoryResponse response, long groupId) {
        return response.items().stream()
                .filter(candidate -> candidate.groupId() == groupId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("group " + groupId + " 기록이 없습니다."));
    }

    private void insertGroup(
            long groupId,
            String status,
            OffsetDateTime completedAt,
            OffsetDateTime cancelledAt,
            long partnerId
    ) {
        // match_groups.attempt_id는 uq_match_groups_attempt로 유일하다. 기록을 여러 건 만들려면
        // 그룹마다 attempt도 따로 있어야 한다.
        long attemptId = 9_131_000L + groupId % 1000;
        jdbc.update("""
                INSERT INTO match_attempts(
                    id, festival_id, target_group_size, status, score, created_by,
                    started_at, expires_at, created_at, updated_at
                ) VALUES (?, 9100001, 2, 'CONFIRMED', 80.00, 'SCHEDULER', ?, ?, ?, ?)
                """, attemptId, NOW, NOW.plusMinutes(2), NOW, NOW);
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count,
                    confirmed_at, completed_at, cancelled_at, created_at, updated_at
                ) VALUES (?, ?, 9100001, ?, 2, ?, ?, ?, ?, ?)
                """, groupId, attemptId, status, NOW, completedAt, cancelledAt, NOW, NOW);
        // uq_match_group_members_member_active는 회원당 활성 참여를 1건으로 제한한다. 종료된
        // 그룹의 참여 상태를 실제와 같이 종료 상태로 넣어야 기록을 여러 건 만들 수 있다.
        String memberStatus = switch (status) {
            case "COMPLETED" -> "COMPLETED";
            case "CANCELLED" -> "CANCELLED";
            default -> "JOINED";
        };
        jdbc.update("""
                INSERT INTO match_group_members(
                    group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES (?, ?, ?, true, ?, ?), (?, ?, ?, true, ?, ?)
                """, groupId, ME, memberStatus, NOW, NOW,
                groupId, partnerId, memberStatus, NOW, NOW);
    }

    private void insertReport(
            long reporterMemberId, long reportedMemberId, long groupId, String reasonCode) {
        jdbc.update("""
                INSERT INTO reports(
                    reporter_member_id, reported_member_id, group_id, reason_code,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'SUBMITTED', ?, ?)
                """, reporterMemberId, reportedMemberId, groupId, reasonCode, NOW, NOW);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TEST_NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
