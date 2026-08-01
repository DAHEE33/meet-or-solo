package com.survey.meetorsolo.domain.matching.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import com.survey.meetorsolo.domain.matching.service.MatchPoolEntryService;
import com.survey.meetorsolo.domain.matching.service.PoolEntryMatchingOrchestrationService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
class MatchingPoolEnteredEventIntegrationTest {

    private static final long FESTIVAL_ID = 9_600_001L;
    private static final long MEMBER_ID = 9_610_001L;

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

    @MockitoBean
    private PoolEntryMatchingOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        reset(orchestrationService);
        jdbc.update("""
                INSERT INTO festivals(
                    id, content_id, content_type_id, title, area_code,
                    status, last_synced_at, created_at, updated_at
                ) VALUES (?, ?, 15, '이벤트 테스트 축제', '32', 'ACTIVE', now(), now(), now())
                """, FESTIVAL_ID, "trigger-event-" + FESTIVAL_ID);
        jdbc.update("""
                INSERT INTO members(
                    id, provider, provider_user_id, nickname, role, status,
                    penalty_score, created_at, updated_at
                ) VALUES (?, 'KAKAO', ?, '이벤트회원', 'USER', 'ACTIVE', 0, now(), now())
                """, MEMBER_ID, "trigger-event-" + MEMBER_ID);
        jdbc.update("""
                INSERT INTO festival_checkins(
                    member_id, festival_id, distance_meters, status,
                    checked_in_at, expires_at, created_at, updated_at
                ) VALUES (?, ?, 10, 'ACTIVE', now() - interval '1 minute',
                          now() + interval '1 hour', now(), now())
                """, MEMBER_ID, FESTIVAL_ID);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM match_pools WHERE member_id = ?", MEMBER_ID);
        jdbc.update("DELETE FROM festival_checkins WHERE member_id = ?", MEMBER_ID);
        jdbc.update("DELETE FROM members WHERE id = ?", MEMBER_ID);
        jdbc.update("DELETE FROM festivals WHERE id = ?", FESTIVAL_ID);
    }

    @Test
    void commit_전에는_handler가_실행되지_않고_commit_후_payload로_실행된다() {
        AtomicBoolean committedPoolVisible = new AtomicBoolean(false);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        doAnswer(invocation -> {
            long poolId = invocation.getArgument(0);
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM match_pools WHERE id = ? AND status = 'WAITING'",
                    Integer.class,
                    poolId
            );
            committedPoolVisible.set(count != null && count == 1);
            return null;
        }).when(orchestrationService).run(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(MEMBER_ID),
                org.mockito.ArgumentMatchers.eq(FESTIVAL_ID)
        );

        long poolId = transaction.execute(status -> {
            var response = entries.enter(MEMBER_ID, request());
            verify(orchestrationService, never()).run(response.poolId(), MEMBER_ID, FESTIVAL_ID);
            return response.poolId();
        });

        // TransactionTemplate 반환 시점에는 동기 AFTER_COMMIT listener 호출이 끝나 있다.
        verify(orchestrationService).run(poolId, MEMBER_ID, FESTIVAL_ID);
        assertThat(committedPoolVisible).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE id = ?",
                Integer.class,
                poolId
        )).isOne();
    }

    @Test
    void 원본_transaction이_rollback되면_handler와_pool_저장이_모두_남지_않는다() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            entries.enter(MEMBER_ID, request());
            status.setRollbackOnly();
        });

        verify(orchestrationService, never()).run(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE member_id = ?",
                Integer.class,
                MEMBER_ID
        )).isZero();
    }

    @Test
    void listener_실패는_신청_결과와_commit된_pool을_되돌리지_않는다() {
        doThrow(new IllegalStateException("trigger failed"))
                .when(orchestrationService).run(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.eq(MEMBER_ID),
                        org.mockito.ArgumentMatchers.eq(FESTIVAL_ID)
                );

        var response = entries.enter(MEMBER_ID, request());

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_pools WHERE id = ? AND status = 'WAITING'",
                Integer.class,
                response.poolId()
        )).isOne();
    }

    private MatchPoolEntryRequest request() {
        return new MatchPoolEntryRequest(FESTIVAL_ID, 2, false, List.of());
    }
}
