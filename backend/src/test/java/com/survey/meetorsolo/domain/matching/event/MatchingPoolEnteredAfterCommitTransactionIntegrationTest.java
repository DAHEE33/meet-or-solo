package com.survey.meetorsolo.domain.matching.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import com.survey.meetorsolo.domain.matching.service.MatchPoolEntryService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
class MatchingPoolEnteredAfterCommitTransactionIntegrationTest {

    private static final long FESTIVAL_ID = 9_800_001L;
    private static final long MEMBER_A_ID = 9_810_001L;
    private static final long MEMBER_B_ID = 9_810_002L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MatchPoolEntryService entries;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbc.update("""
                INSERT INTO festivals(
                    id, content_id, content_type_id, title, area_code,
                    status, last_synced_at, created_at, updated_at
                ) VALUES (?, ?, 15, 'AFTER_COMMIT 테스트 축제', '32', 'ACTIVE', now(), now(), now())
                """, FESTIVAL_ID, "after-commit-" + FESTIVAL_ID);
        insertMember(MEMBER_A_ID, "회원A");
        insertMember(MEMBER_B_ID, "회원B");
        insertCheckin(MEMBER_A_ID);
        insertCheckin(MEMBER_B_ID);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM match_proposals WHERE member_id IN (?, ?)", MEMBER_A_ID, MEMBER_B_ID);
        jdbc.update("DELETE FROM match_attempt_members WHERE member_id IN (?, ?)", MEMBER_A_ID, MEMBER_B_ID);
        jdbc.update("""
                DELETE FROM match_attempts
                WHERE festival_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM match_attempt_members member
                      WHERE member.attempt_id = match_attempts.id
                  )
                """, FESTIVAL_ID);
        jdbc.update("DELETE FROM match_pools WHERE member_id IN (?, ?)", MEMBER_A_ID, MEMBER_B_ID);
        jdbc.update("DELETE FROM festival_checkins WHERE member_id IN (?, ?)", MEMBER_A_ID, MEMBER_B_ID);
        jdbc.update("DELETE FROM members WHERE id IN (?, ?)", MEMBER_A_ID, MEMBER_B_ID);
        jdbc.update("DELETE FROM festivals WHERE id = ?", FESTIVAL_ID);
    }

    @Test
    void 두번째_pool_생성_commit_직후_AFTER_COMMIT_trigger가_attempt와_proposal을_생성한다() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        long poolAId = transaction.execute(status -> entries.enter(MEMBER_A_ID, request()).poolId());

        assertThat(poolStatus(poolAId)).isEqualTo("WAITING");
        assertThat(lockToken(poolAId)).isNull();
        assertThat(count("match_attempts")).isZero();
        assertThat(count("match_proposals")).isZero();

        long poolBId = transaction.execute(status -> entries.enter(MEMBER_B_ID, request()).poolId());

        assertThat(poolStatus(poolAId)).isEqualTo("PROPOSED");
        assertThat(poolStatus(poolBId)).isEqualTo("PROPOSED");
        assertThat(lockToken(poolAId)).isNull();
        assertThat(lockToken(poolBId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_attempts WHERE festival_id = ? AND created_by = 'POOL_ENTRY'",
                Integer.class,
                FESTIVAL_ID
        )).isOne();
        assertThat(count("match_attempt_members")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_proposals WHERE member_id = ?",
                Integer.class,
                MEMBER_A_ID
        )).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_proposals WHERE member_id = ?",
                Integer.class,
                MEMBER_B_ID
        )).isOne();
    }

    private void insertMember(long memberId, String nickname) {
        jdbc.update("""
                INSERT INTO members(
                    id, provider, provider_user_id, nickname, role, status,
                    penalty_score, created_at, updated_at
                ) VALUES (?, 'KAKAO', ?, ?, 'USER', 'ACTIVE', 0, now(), now())
                """, memberId, "after-commit-" + memberId, nickname);
    }

    private void insertCheckin(long memberId) {
        jdbc.update("""
                INSERT INTO festival_checkins(
                    member_id, festival_id, distance_meters, status,
                    checked_in_at, expires_at, created_at, updated_at
                ) VALUES (?, ?, 10, 'ACTIVE', now() - interval '1 minute',
                          now() + interval '1 hour', now(), now())
                """, memberId, FESTIVAL_ID);
    }

    private MatchPoolEntryRequest request() {
        return new MatchPoolEntryRequest(FESTIVAL_ID, 2, false, List.of());
    }

    private String poolStatus(long poolId) {
        return jdbc.queryForObject("SELECT status FROM match_pools WHERE id = ?", String.class, poolId);
    }

    private String lockToken(long poolId) {
        return jdbc.queryForObject("SELECT lock_token FROM match_pools WHERE id = ?", String.class, poolId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
