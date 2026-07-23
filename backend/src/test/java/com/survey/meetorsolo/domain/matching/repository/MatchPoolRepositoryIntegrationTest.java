package com.survey.meetorsolo.domain.matching.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Sql("/fixtures/matching-engine-foundation.sql")
class MatchPoolRepositoryIntegrationTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final long FESTIVAL_ID = 9_100_001L;
    private static final long REQUESTER_MEMBER_ID = 9_110_001L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MatchPoolRepository matchPoolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void V1부터_V11과_pgvector_extension이_적용된다() {
        List<String> successfulVersions = jdbcTemplate.queryForList(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank
                """,
                String.class
        );
        Integer vectorExtensionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class
        );

        assertThat(successfulVersions).contains(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        assertThat(vectorExtensionCount).isEqualTo(1);
    }

    @Test
    void 같은_축제의_유효한_WAITING_후보를_entered_at과_id_순으로_조회한다() {
        assertThat(candidateMemberIds()).containsExactly(9_110_011L, 9_110_002L);
    }

    @Test
    void 요청자_자신을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(REQUESTER_MEMBER_ID);
    }

    @Test
    void 다른_축제의_pool을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(9_110_004L);
    }

    @Test
    void WAITING이_아닌_pool을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(9_110_005L);
    }

    @Test
    void 만료된_pool을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(9_110_003L);
    }

    @Test
    void 만료된_체크인의_pool을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(9_110_008L);
    }

    @Test
    void ACTIVE가_아닌_체크인의_pool을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(9_110_009L);
    }

    @Test
    void active_cooldown_회원을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(9_110_007L);
    }

    @Test
    void 요청자가_차단한_회원을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(9_110_006L);
    }

    @Test
    void 요청자를_차단한_회원을_제외한다() {
        assertThat(candidateMemberIds()).doesNotContain(9_110_010L);
    }

    @Test
    void 동일_회원의_active_pool_중복을_partial_unique_index가_차단한다() {
        MatchPool duplicateActivePool = waitingPool(9_110_002L, 9_120_002L);

        Throwable thrown = catchThrowable(() -> matchPoolRepository.saveAndFlush(duplicateActivePool));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rootCauseOf(thrown).getMessage()).contains("uq_match_pools_member_active");
    }

    @Test
    void 종료_상태_pool_이후에는_새로운_active_pool을_허용한다() {
        jdbcTemplate.update(
                "UPDATE match_pools SET status = 'CANCELLED', updated_at = ? WHERE member_id = ?",
                NOW,
                9_110_002L
        );
        MatchPool nextActivePool = waitingPool(9_110_002L, 9_120_002L);

        assertThatCode(() -> matchPoolRepository.saveAndFlush(nextActivePool))
                .doesNotThrowAnyException();
        assertThat(matchPoolRepository.findById(nextActivePool.getId()))
                .get()
                .extracting(MatchPool::getStatus)
                .isEqualTo(MatchPool.STATUS_WAITING);
    }

    private List<Long> candidateMemberIds() {
        return matchPoolRepository.findEligibleWaitingCandidates(FESTIVAL_ID, REQUESTER_MEMBER_ID, NOW)
                .stream()
                .map(MatchPool::getMemberId)
                .toList();
    }

    private MatchPool waitingPool(long memberId, long checkinId) {
        return MatchPool.waiting(
                memberId,
                FESTIVAL_ID,
                checkinId,
                4,
                true,
                List.of("PHOTO"),
                NOW.plusSeconds(1),
                NOW.plusSeconds(61)
        );
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
