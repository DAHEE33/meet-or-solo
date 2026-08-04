package com.survey.meetorsolo.domain.matching.service;

import static com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupEventsResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchCancellationReason;
import com.survey.meetorsolo.domain.matching.dto.MatchingStateChangedNotification;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
@Import(MatchArrivalTimeServiceIntegrationTest.FixedClockConfiguration.class)
@Sql(
        scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class MatchArrivalTimeServiceIntegrationTest {
    private static final java.time.OffsetDateTime TEST_NOW =
            NOW.plusSeconds(10).truncatedTo(ChronoUnit.MICROS);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MatchArrivalTimeService service;
    @Autowired
    private MatchArrivalService arrivals;
    @Autowired
    private MatchCancellationService cancellations;
    @Autowired
    private MatchNoShowGroupService noShows;

    @Autowired
    private MatchGroupQueryService queries;

    @Autowired
    private MatchGroupEventQueryService eventQueries;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void V14_무패널티_취소는_2인_group을_종료하고_비귀책을_LEFT로_전환한다() {
        var result = cancellations.cancel(
                9_110_001L, MatchCancellationReason.TRANSPORTATION_ISSUE);

        assertThat(result.groupContinues()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM match_groups WHERE id=9171001", String.class))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM match_group_members WHERE id=9181001", String.class))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM match_group_members WHERE id=9181002", String.class))
                .isEqualTo("LEFT");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM match_penalty_events
                WHERE related_group_id=9171001
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM match_events
                WHERE group_id=9171001
                  AND event_type IN ('MEMBER_CANCELLED','MATCH_CANCELLED')
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void V14_NO_SHOW는_재실행해도_member_event_penalty_cooldown을_중복하지_않는다() {
        var deadline = NOW.plusSeconds(10).plusMinutes(30);

        assertThat(noShows.process(9_171_001L, deadline)).isTrue();
        assertThat(noShows.process(9_171_001L, deadline)).isFalse();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM match_group_members
                WHERE group_id=9171001 AND status='NO_SHOW'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM match_events
                WHERE group_id=9171001 AND event_type='MEMBER_NO_SHOW'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM match_penalty_events
                WHERE related_group_id=9171001 AND event_type='NO_SHOW'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM match_cooldowns
                WHERE related_group_id=9171001 AND reason='NO_SHOW'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT sum(penalty_score) FROM members WHERE id IN (9110001,9110002)
                """, Integer.class)).isEqualTo(6);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count,
                    confirmed_at, created_at, updated_at
                ) VALUES (9171001, 9130001, 9100001, 'CONFIRMED', 2, ?, ?, ?)
                """, NOW.plusSeconds(10), NOW.plusSeconds(10), NOW.plusSeconds(10));
        jdbc.update("""
                INSERT INTO match_group_members(
                    id, group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES
                    (9181001, 9171001, 9110001, 'JOINED', true, ?, ?),
                    (9181002, 9171001, 9110002, 'JOINED', true, ?, ?)
                """, NOW, NOW, NOW, NOW);
        clearInvocations(messagingTemplate);
    }

    @AfterEach
    void dropFailureTriggers() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_arrival_member_update ON match_group_members");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_arrival_group_update ON match_groups");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_arrival_event_insert ON match_events");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_arrival_member_update()");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_arrival_group_update()");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_arrival_event_insert()");
    }

    @Test
    void JOINED에서_도착_시간을_선택하고_current_snapshot과_전체_회원_알림을_갱신한다() {
        MatchGroupResponse snapshot = service.select(9_110_001L, 10);

        assertThat(snapshot.members()).extracting(member -> member.memberId())
                .containsExactly(9_110_001L, 9_110_002L);
        assertThat(snapshot.members().get(0).status()).isEqualTo("ARRIVAL_TIME_SELECTED");
        assertThat(snapshot.members().get(0).arrivalMinutes()).isEqualTo(10);
        assertThat(snapshot.members().get(0).arrivalTimeSelectedAt())
                .isEqualTo(NOW.plusSeconds(10));
        assertThat(snapshot.arrivalDeadlineAt())
                .isEqualTo(NOW.plusMinutes(30).plusSeconds(10));
        assertThat(eventMinutes()).containsExactly(10);
        verify(messagingTemplate, timeout(1_000)).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("9110001"),
                org.mockito.ArgumentMatchers.eq("/queue/matching"),
                org.mockito.ArgumentMatchers.any()
        );
        verify(messagingTemplate, timeout(1_000)).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("9110002"),
                org.mockito.ArgumentMatchers.eq("/queue/matching"),
                org.mockito.ArgumentMatchers.any()
        );
        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("9110003"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void current_group_event는_actor를_active_member로_제한하고_결정적_오름차순으로_반환한다() {
        jdbc.update("""
                INSERT INTO match_events(
                    group_id, attempt_id, member_id, event_type, payload, created_at
                ) VALUES
                    (9171001, 9130001, NULL, 'MATCH_CONFIRMED', '{}'::jsonb, ?),
                    (9171001, 9130001, 9110001, 'ARRIVAL_TIME_SELECTED',
                     '{"arrivalMinutes":10}'::jsonb, ?),
                    (9171001, 9130001, 9110002, 'MEMBER_ARRIVED', '{}'::jsonb, ?),
                    (9171001, 9130001, 9110003, 'MEMBER_ARRIVED', '{}'::jsonb, ?)
                """, NOW, NOW.plusSeconds(1), NOW.plusSeconds(2), NOW.plusSeconds(3));

        MatchGroupEventsResponse response = eventQueries.currentGroupEvents(9_110_001L);

        assertThat(response.events()).extracting(event -> event.type())
                .containsExactly(
                        "MATCH_CONFIRMED",
                        "ARRIVAL_TIME_SELECTED",
                        "MEMBER_ARRIVED",
                        "MEMBER_ARRIVED"
                );
        assertThat(response.events().get(0).actor()).isNull();
        assertThat(response.events().get(1).actor().memberId()).isEqualTo(9_110_001L);
        assertThat(response.events().get(1).actor().nickname()).isEqualTo("fixture9110001");
        assertThat(response.events().get(1).arrivalMinutes()).isEqualTo(10);
        assertThat(response.events().get(2).actor().memberId()).isEqualTo(9_110_002L);
        assertThat(response.events().get(3).actor()).isNull();
        assertThat(response.events()).extracting(event -> event.occurredAt())
                .isSorted();
    }

    @Test
    void latest_50건만_선택한_뒤_응답은_createdAt_ID_오름차순이다() {
        for (int index = 0; index < 55; index++) {
            jdbc.update("""
                    INSERT INTO match_events(
                        group_id, attempt_id, member_id, event_type, payload, created_at
                    ) VALUES (9171001, 9130001, 9110001, 'MEMBER_ARRIVED', '{}'::jsonb, ?)
                    """, NOW.plusSeconds(index));
        }

        MatchGroupEventsResponse response = eventQueries.currentGroupEvents(9_110_001L);

        assertThat(response.events()).hasSize(50);
        assertThat(response.events()).extracting(event -> event.occurredAt()).isSorted();
        assertThat(response.events().get(0).occurredAt())
                .isEqualTo(NOW.plusSeconds(5));
        assertThat(response.events().get(49).occurredAt())
                .isEqualTo(NOW.plusSeconds(54));
    }

    @Test
    void malformed_도착_payload는_제외하고_API_전체는_성공한다() {
        jdbc.update("""
                INSERT INTO match_events(
                    group_id, attempt_id, member_id, event_type, payload, created_at
                ) VALUES
                    (9171001, 9130001, 9110001, 'ARRIVAL_TIME_SELECTED',
                     '{"arrivalMinutes":15}'::jsonb, ?),
                    (9171001, 9130001, 9110001, 'ARRIVAL_TIME_SELECTED',
                     '{"arrivalMinutes":"ten"}'::jsonb, ?),
                    (9171001, 9130001, 9110001, 'MEMBER_ARRIVED', '{}'::jsonb, ?)
                """, NOW, NOW.plusSeconds(1), NOW.plusSeconds(2));

        MatchGroupEventsResponse response = eventQueries.currentGroupEvents(9_110_001L);

        assertThat(response.events()).singleElement()
                .extracting(event -> event.type())
                .isEqualTo("MEMBER_ARRIVED");
    }

    @Test
    void 종료_group은_current_event를_조회할_수_없다() {
        jdbc.update("UPDATE match_groups SET status = 'COMPLETED' WHERE id = 9171001");

        assertThat(eventQueries.currentGroupEvents(9_110_001L)).isNull();
    }

    @Test
    void arrival_time과_arrival_멱등_요청은_timeline_event를_늘리지_않는다() {
        service.select(9_110_001L, 10);
        service.select(9_110_001L, 10);
        arrivals.arrive(9_110_001L);
        arrivals.arrive(9_110_001L);

        MatchGroupEventsResponse response = eventQueries.currentGroupEvents(9_110_001L);

        assertThat(response.events()).extracting(event -> event.type())
                .containsExactly("ARRIVAL_TIME_SELECTED", "MEMBER_ARRIVED");
    }

    @Test
    void 첫_도착은_member와_group을_갱신하고_반복_요청은_멱등이다() {
        service.select(9_110_001L, 10);
        clearInvocations(messagingTemplate);

        MatchGroupResponse first = arrivals.arrive(9_110_001L);
        MatchGroupResponse repeated = arrivals.arrive(9_110_001L);

        assertThat(first.status()).isEqualTo("IN_PROGRESS");
        assertThat(first.startedAt()).isNotNull();
        assertThat(first.members().get(0).status()).isEqualTo("ARRIVED");
        assertThat(first.members().get(0).arrivalMinutes()).isEqualTo(10);
        assertThat(first.members().get(0).arrivedAt()).isEqualTo(first.startedAt());
        assertThat(repeated.startedAt()).isEqualTo(first.startedAt());
        assertThat(repeated.members().get(0).arrivedAt())
                .isEqualTo(first.members().get(0).arrivedAt());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM match_events
                WHERE group_id = 9171001
                  AND member_id = 9110001
                  AND event_type = 'MEMBER_ARRIVED'
                """, Integer.class)).isEqualTo(1);
        verify(messagingTemplate, timeout(1_000).times(1)).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("9110001"),
                org.mockito.ArgumentMatchers.eq("/queue/matching"),
                org.mockito.ArgumentMatchers.argThat((MatchingStateChangedNotification notification) ->
                        "MEMBER_ARRIVED".equals(notification.reason()))
        );
    }

    @Test
    void 서로_다른_회원의_동시_도착은_group_lock으로_직렬화되고_최종_snapshot이_일치한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MatchGroupResponse> first = executor.submit(() -> {
                await(start);
                return arrivals.arrive(9_110_001L);
            });
            Future<MatchGroupResponse> second = executor.submit(() -> {
                await(start);
                return arrivals.arrive(9_110_002L);
            });

            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            MatchGroupResponse firstSnapshot = queries.currentGroup(9_110_001L);
            MatchGroupResponse secondSnapshot = queries.currentGroup(9_110_002L);
            assertThat(firstSnapshot.status()).isEqualTo("IN_PROGRESS");
            assertThat(firstSnapshot.startedAt()).isNotNull();
            assertThat(firstSnapshot.confirmedAt()).isEqualTo(NOW.plusSeconds(10));
            assertThat(firstSnapshot.confirmedMemberCount()).isEqualTo(2);
            assertThat(firstSnapshot.members()).hasSize(2)
                    .allSatisfy(member -> {
                        assertThat(member.status()).isEqualTo("ARRIVED");
                        assertThat(member.arrivedAt()).isNotNull();
                    });
            assertThat(secondSnapshot.status()).isEqualTo(firstSnapshot.status());
            assertThat(secondSnapshot.startedAt()).isEqualTo(firstSnapshot.startedAt());
            assertThat(secondSnapshot.confirmedAt()).isEqualTo(firstSnapshot.confirmedAt());
            assertThat(secondSnapshot.members()).isEqualTo(firstSnapshot.members());

            assertThat(memberArrivedEventCounts()).containsExactly(
                    new MemberEventCount(9_110_001L, 1L),
                    new MemberEventCount(9_110_002L, 1L)
            );
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM match_group_members
                    WHERE group_id = 9171001
                      AND status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
                    """, Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT status
                    FROM match_groups
                    WHERE id = 9171001
                    """, String.class)).isEqualTo("IN_PROGRESS");

            verifyMemberArrivedNotification("9110001", 2);
            verifyMemberArrivedNotification("9110002", 2);
            verify(messagingTemplate, never()).convertAndSendToUser(
                    org.mockito.ArgumentMatchers.eq("9110003"),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any()
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 동일_회원의_동시_도착은_event와_알림을_한_번만_생성한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MatchGroupResponse> first = executor.submit(() -> {
                await(start);
                return arrivals.arrive(9_110_001L);
            });
            Future<MatchGroupResponse> second = executor.submit(() -> {
                await(start);
                return arrivals.arrive(9_110_001L);
            });

            start.countDown();
            MatchGroupResponse firstSnapshot = first.get(10, TimeUnit.SECONDS);
            MatchGroupResponse secondSnapshot = second.get(10, TimeUnit.SECONDS);

            MatchGroupResponse finalSnapshot = queries.currentGroup(9_110_001L);
            assertThat(firstSnapshot.members().get(0).status()).isEqualTo("ARRIVED");
            assertThat(secondSnapshot.members().get(0).status()).isEqualTo("ARRIVED");
            assertThat(finalSnapshot.members().get(0).arrivedAt()).isNotNull();
            assertThat(secondSnapshot.members().get(0).arrivedAt())
                    .isEqualTo(firstSnapshot.members().get(0).arrivedAt());
            assertThat(finalSnapshot.members().get(0).arrivedAt())
                    .isEqualTo(firstSnapshot.members().get(0).arrivedAt());
            assertThat(firstSnapshot.startedAt()).isEqualTo(secondSnapshot.startedAt());
            assertThat(finalSnapshot.startedAt()).isEqualTo(firstSnapshot.startedAt());
            assertThat(memberArrivedEventCounts()).containsExactly(
                    new MemberEventCount(9_110_001L, 1L)
            );
            verifyMemberArrivedNotification("9110001", 1);
            verifyMemberArrivedNotification("9110002", 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 도착_member_update_실패는_member_group_event와_알림을_모두_rollback한다() {
        createFailureTrigger("match_group_members", "fail_arrival_member_update", "UPDATE");

        assertThatThrownBy(() -> arrivals.arrive(9_110_001L))
                .isInstanceOf(RuntimeException.class);

        assertArrivalUnchangedAndNoNotification();
    }

    @Test
    void 도착_group_update_실패는_member_group_event와_알림을_모두_rollback한다() {
        createFailureTrigger("match_groups", "fail_arrival_group_update", "UPDATE");

        assertThatThrownBy(() -> arrivals.arrive(9_110_001L))
                .isInstanceOf(RuntimeException.class);

        assertArrivalUnchangedAndNoNotification();
    }

    @Test
    void 이미_IN_PROGRESS인_group은_불필요한_group_update없이_member만_도착_처리한다() {
        jdbc.update("""
                UPDATE match_groups
                SET status = 'IN_PROGRESS', started_at = ?, updated_at = ?
                WHERE id = 9171001
                """, NOW.plusSeconds(5), NOW.plusSeconds(5));
        createFailureTrigger("match_groups", "fail_arrival_group_update", "UPDATE");

        MatchGroupResponse snapshot = arrivals.arrive(9_110_001L);

        assertThat(snapshot.status()).isEqualTo("IN_PROGRESS");
        assertThat(snapshot.startedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(snapshot.members().get(0).status()).isEqualTo("ARRIVED");
        assertThat(memberArrivedEventCounts()).containsExactly(
                new MemberEventCount(9_110_001L, 1L)
        );
    }

    @Test
    void MEMBER_ARRIVED_event_insert_실패는_member_group_event와_알림을_모두_rollback한다() {
        createFailureTrigger("match_events", "fail_arrival_event_insert", "INSERT");

        assertThatThrownBy(() -> arrivals.arrive(9_110_001L))
                .isInstanceOf(RuntimeException.class);

        assertArrivalUnchangedAndNoNotification();
    }

    @Test
    void 같은_값은_멱등이고_다른_값은_event를_한_건_추가한다() {
        service.select(9_110_001L, 10);
        clearInvocations(messagingTemplate);

        service.select(9_110_001L, 10);
        assertThat(eventMinutes()).containsExactly(10);
        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );

        service.select(9_110_001L, 5);
        assertThat(eventMinutes()).containsExactly(10, 5);
        assertThat(queries.currentGroup(9_110_001L).members().get(0).arrivalMinutes())
                .isEqualTo(5);
    }

    @Test
    void 신규_허용값_5_10_20_25분은_member와_event에_저장된다() {
        for (int minutes : List.of(5, 10, 20, 25)) {
            MatchGroupResponse snapshot = service.select(9_110_001L, minutes);
            assertThat(snapshot.members().get(0).arrivalMinutes()).isEqualTo(minutes);
            assertThat(snapshot.arrivalDeadlineAt())
                    .isEqualTo(NOW.plusMinutes(30).plusSeconds(10));
            assertThat(snapshot.members().get(0).arrivalTimeSelectedAt().plusMinutes(minutes)
                    .isAfter(snapshot.arrivalDeadlineAt())).isFalse();
        }
    }

    @Test
    void 도착_25분_예상_시각이_deadline과_같으면_허용하고_넘으면_거절한다() {
        jdbc.update(
                "UPDATE match_groups SET confirmed_at = ? WHERE id = 9171001",
                TEST_NOW.minusMinutes(5)
        );

        MatchGroupResponse boundary = service.select(9_110_001L, 25);
        assertThat(boundary.members().get(0).arrivalTimeSelectedAt().plusMinutes(25))
                .isEqualTo(boundary.arrivalDeadlineAt());

        jdbc.update(
                """
                UPDATE match_group_members
                SET status = 'JOINED', arrival_minutes = NULL, arrival_time_selected_at = NULL
                WHERE id = 9181001
                """
        );
        jdbc.update("DELETE FROM match_events WHERE group_id = 9171001");
        jdbc.update(
                "UPDATE match_groups SET confirmed_at = ? WHERE id = 9171001",
                TEST_NOW.minusMinutes(5).minusSeconds(1)
        );
        clearInvocations(messagingTemplate);

        assertDeadlineExceeded(() -> service.select(9_110_001L, 25));
        assertUnchangedAndNoNotification();
    }

    @Test
    void 남은_시간보다_긴_선택은_member_event_알림_변경없이_거절한다() {
        jdbc.update(
                "UPDATE match_groups SET confirmed_at = ? WHERE id = 9171001",
                NOW.minusMinutes(20).plusSeconds(10)
        );

        assertDeadlineExceeded(() -> service.select(9_110_001L, 20));

        assertUnchangedAndNoNotification();
        assertThat(queries.currentGroup(9_110_001L).arrivalDeadlineAt())
                .isEqualTo(NOW.plusMinutes(10).plusSeconds(10));
    }

    @Test
    void 남은_5분_경계에서는_5분을_허용하고_deadline부터는_선택할_수_없다() {
        jdbc.update(
                "UPDATE match_groups SET confirmed_at = ? WHERE id = 9171001",
                TEST_NOW.minusMinutes(25)
        );

        MatchGroupResponse justBefore = service.select(9_110_001L, 5);
        assertThat(justBefore.members().get(0).arrivalMinutes()).isEqualTo(5);

        jdbc.update(
                """
                UPDATE match_group_members
                SET status = 'JOINED', arrival_minutes = NULL, arrival_time_selected_at = NULL
                WHERE id = 9181001
                """
        );
        jdbc.update("DELETE FROM match_events WHERE group_id = 9171001");
        jdbc.update(
                "UPDATE match_groups SET confirmed_at = ? WHERE id = 9171001",
                TEST_NOW.minusMinutes(30)
        );
        clearInvocations(messagingTemplate);

        assertDeadlineExceeded(() -> service.select(9_110_001L, 5));
        assertUnchangedAndNoNotification();
    }

    @Test
    void 같은_값과_다른_값_변경은_deadline을_연장하지_않는다() {
        MatchGroupResponse first = service.select(9_110_001L, 20);
        clearInvocations(messagingTemplate);

        MatchGroupResponse repeated = service.select(9_110_001L, 20);
        MatchGroupResponse changed = service.select(9_110_001L, 5);

        assertThat(repeated.arrivalDeadlineAt()).isEqualTo(first.arrivalDeadlineAt());
        assertThat(changed.arrivalDeadlineAt()).isEqualTo(first.arrivalDeadlineAt());
        assertThat(repeated.members().get(0).arrivalTimeSelectedAt())
                .isEqualTo(first.members().get(0).arrivalTimeSelectedAt());
        assertThat(eventMinutes()).containsExactly(20, 5);
    }

    @Test
    void 과거_30분_row는_current_group에서_조회할_수_있다() {
        jdbc.update(
                "UPDATE match_groups SET confirmed_at = ? WHERE id = 9171001",
                NOW.minusMinutes(20).plusSeconds(10)
        );
        jdbc.update(
                """
                UPDATE match_group_members
                SET status = 'ARRIVAL_TIME_SELECTED',
                    arrival_minutes = 30,
                    arrival_time_selected_at = ?,
                    updated_at = ?
                WHERE id = 9181001
                """,
                NOW.minusMinutes(20).plusSeconds(10),
                NOW.minusMinutes(20).plusSeconds(10)
        );

        MatchGroupResponse legacy = queries.currentGroup(9_110_001L);

        assertThat(legacy.members().get(0).arrivalMinutes()).isEqualTo(30);
        assertThat(legacy.members().get(0).arrivalTimeSelectedAt())
                .isEqualTo(NOW.minusMinutes(20).plusSeconds(10));
        assertThat(legacy.arrivalDeadlineAt())
                .isEqualTo(NOW.plusMinutes(10).plusSeconds(10));
        assertThat(eventMinutes()).isEmpty();
    }

    @Test
    void ARRIVED_inactive_member와_terminal_group은_변경할_수_없다() {
        jdbc.update("UPDATE match_group_members SET status = 'ARRIVED' WHERE id = 9181001");
        assertThatThrownBy(() -> service.select(9_110_001L, 5))
                .isInstanceOf(BusinessException.class);

        jdbc.update("UPDATE match_group_members SET status = 'LEFT' WHERE id = 9181001");
        assertThatThrownBy(() -> service.select(9_110_001L, 5))
                .isInstanceOf(BusinessException.class);

        jdbc.update("UPDATE match_group_members SET status = 'JOINED' WHERE id = 9181001");
        jdbc.update("UPDATE match_groups SET status = 'COMPLETED' WHERE id = 9171001");
        assertThatThrownBy(() -> service.select(9_110_001L, 5))
                .isInstanceOf(BusinessException.class);

        jdbc.update("UPDATE match_groups SET status = 'CANCELLED' WHERE id = 9171001");
        assertThatThrownBy(() -> service.select(9_110_001L, 5))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 동일_회원의_동시_변경은_직렬화되고_최종값과_event_이력이_일치한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                await(start);
                service.select(9_110_001L, 5);
            });
            Future<?> second = executor.submit(() -> {
                await(start);
                service.select(9_110_001L, 20);
            });
            start.countDown();
            first.get();
            second.get();

            List<Integer> events = eventMinutes();
            Integer current = jdbc.queryForObject("""
                    SELECT arrival_minutes
                    FROM match_group_members
                    WHERE id = 9181001
                    """, Integer.class);
            assertThat(events).hasSize(2);
            assertThat(current).isEqualTo(events.get(events.size() - 1));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void member_update_실패는_상태_event_알림을_모두_rollback한다() {
        createFailureTrigger("match_group_members", "fail_arrival_member_update", "UPDATE");

        assertThatThrownBy(() -> service.select(9_110_001L, 10))
                .isInstanceOf(RuntimeException.class);
        assertUnchangedAndNoNotification();
    }

    @Test
    void match_event_저장_실패는_member_변경과_알림을_rollback한다() {
        createFailureTrigger("match_events", "fail_arrival_event_insert", "INSERT");

        assertThatThrownBy(() -> service.select(9_110_001L, 10))
                .isInstanceOf(RuntimeException.class);
        assertUnchangedAndNoNotification();
    }

    private List<Integer> eventMinutes() {
        return jdbc.queryForList("""
                SELECT (payload ->> 'arrivalMinutes')::integer
                FROM match_events
                WHERE group_id = 9171001
                  AND member_id = 9110001
                  AND event_type = 'ARRIVAL_TIME_SELECTED'
                ORDER BY id
                """, Integer.class);
    }

    private List<MemberEventCount> memberArrivedEventCounts() {
        return jdbc.query("""
                SELECT member_id, COUNT(*) AS event_count
                FROM match_events
                WHERE group_id = 9171001
                  AND event_type = 'MEMBER_ARRIVED'
                GROUP BY member_id
                ORDER BY member_id
                """, (resultSet, rowNumber) -> new MemberEventCount(
                resultSet.getLong("member_id"),
                resultSet.getLong("event_count")
        ));
    }

    private void createFailureTrigger(String table, String function, String operation) {
        jdbc.execute("""
                CREATE FUNCTION %s() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced arrival failure';
                END;
                $$ LANGUAGE plpgsql
                """.formatted(function));
        jdbc.execute("""
                CREATE TRIGGER %s
                BEFORE %s ON %s
                FOR EACH ROW EXECUTE FUNCTION %s()
                """.formatted(function, operation, table, function));
    }

    private void assertUnchangedAndNoNotification() {
        assertThat(jdbc.queryForObject("""
                SELECT status
                FROM match_group_members
                WHERE id = 9181001
                """, String.class)).isEqualTo("JOINED");
        assertThat(eventMinutes()).isEmpty();
        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private void assertArrivalUnchangedAndNoNotification() {
        assertThat(jdbc.queryForObject("""
                SELECT status
                FROM match_group_members
                WHERE id = 9181001
                """, String.class)).isEqualTo("JOINED");
        assertThat(jdbc.queryForObject("""
                SELECT arrived_at IS NULL
                FROM match_group_members
                WHERE id = 9181001
                """, Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT status
                FROM match_groups
                WHERE id = 9171001
                """, String.class)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("""
                SELECT started_at IS NULL
                FROM match_groups
                WHERE id = 9171001
                """, Boolean.class)).isTrue();
        assertThat(memberArrivedEventCounts()).isEmpty();
        MatchGroupResponse snapshot = queries.currentGroup(9_110_001L);
        assertThat(snapshot.status()).isEqualTo("CONFIRMED");
        assertThat(snapshot.startedAt()).isNull();
        assertThat(snapshot.members().get(0).status()).isEqualTo("JOINED");
        assertThat(snapshot.members().get(0).arrivedAt()).isNull();
        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private void assertDeadlineExceeded(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED));
    }

    private void verifyMemberArrivedNotification(String memberId, int times) {
        verify(messagingTemplate, timeout(1_000).times(times)).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq(memberId),
                org.mockito.ArgumentMatchers.eq("/queue/matching"),
                org.mockito.ArgumentMatchers.argThat((MatchingStateChangedNotification notification) ->
                        "MEMBER_ARRIVED".equals(notification.reason()))
        );
    }

    private record MemberEventCount(long memberId, long eventCount) {
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedArrivalClock() {
            return Clock.fixed(TEST_NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
