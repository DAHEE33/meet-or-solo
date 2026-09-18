package com.survey.meetorsolo.domain.matching;

import static com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.matching.service.MatchMeetingCloseGroupService;
import com.survey.meetorsolo.domain.matching.service.MatchMeetingWindowPolicy;
import com.survey.meetorsolo.domain.matching.service.MatchNoShowGroupService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * {@code docs/19} 4.11에서 정한 만남 수명주기를 <b>HTTP 경로로 끝까지</b> 확인하는 하네스다.
 *
 * <p>개별 서비스 테스트와 목적이 다르다. 그쪽은 한 서비스의 규칙을 고정하고, 여기서는 컨트롤러 →
 * 서비스 → 배치 → 보상 이벤트 → 재매칭 제한 조회까지 <b>연결이 실제로 이어지는지</b>를 본다.
 * 규칙을 하나씩 통과해도 그 사이가 끊기면 사용자에게는 아무것도 동작하지 않는다.
 *
 * <p>시나리오는 정책 결정 하나에 하나씩 대응한다.
 *
 * <ol>
 *   <li>전원 도착해도 방은 유지되고, 만남 시간이 끝나야 완료·보상·잠금 해제가 일어난다</li>
 *   <li>도착 반경 밖에서는 도착이 거절된다</li>
 *   <li>만남 성립 전에는 먼저 나가기가 막히고 참여 취소만 열린다(페널티 회피 차단)</li>
 *   <li>만남 성립 후에는 먼저 나가도 방이 유지되고 나간 사람도 보상을 받는다</li>
 *   <li>도착자가 없던 매칭은 신고할 수 없고, 도착자가 있으면 신고할 수 있다</li>
 * </ol>
 *
 * <p>이 클래스에 {@code @Transactional}을 붙이면 안 된다. 매너온도 보상이 AFTER_COMMIT에서
 * 지급되므로 롤백되면 검증하려는 연결이 통째로 사라진다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=match-meeting-lifecycle-harness-secret",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false",
        // local 프로필 기본값은 우회(true)다. 반경 검증 자체를 확인해야 하므로 운영 기본값을 쓴다.
        "app.matching.arrival.bypass-radius-check=false",
        "app.matching.arrival.radius-meters=150"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(MatchMeetingLifecycleHarnessTest.FixedClockConfiguration.class)
@Sql(
        scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class MatchMeetingLifecycleHarnessTest {

    private static final long GROUP_ID = 9_179_001L;
    private static final long ME = 9_110_001L;
    private static final long PARTNER = 9_110_002L;
    private static final OffsetDateTime CONFIRMED_AT = NOW.plusSeconds(10);
    private static final OffsetDateTime TEST_NOW = CONFIRMED_AT;

    /** 만남 장소 핀. 반경 검증의 기준점이다. */
    private static final BigDecimal MEETING_LATITUDE = new BigDecimal("37.8813");
    private static final BigDecimal MEETING_LONGITUDE = new BigDecimal("127.7300");
    private static final String AT_MEETING_POINT =
            "{\"latitude\":37.8813,\"longitude\":127.7300}";
    /** 만남 장소에서 1km 넘게 떨어진 지점. */
    private static final String FAR_AWAY = "{\"latitude\":37.8950,\"longitude\":127.7300}";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MatchMeetingCloseGroupService meetingCloses;
    @Autowired private MatchNoShowGroupService noShows;

    @BeforeEach
    void insertConfirmedGroup() {
        jdbc.update("""
                INSERT INTO match_attempts(
                    id, festival_id, target_group_size, status, score, created_by,
                    started_at, expires_at, created_at, updated_at
                ) VALUES (9139001, 9100001, 2, 'CONFIRMED', 80.00, 'SCHEDULER', ?, ?, ?, ?)
                """, NOW, NOW.plusMinutes(2), NOW, NOW);
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count,
                    meeting_place_name, meeting_place_address, meeting_place_content_id,
                    meeting_map_x, meeting_map_y, confirmed_at, created_at, updated_at
                ) VALUES (?, 9139001, 9100001, 'CONFIRMED', 2,
                    '남춘천역 광장', '강원 춘천시', 'harness-place', ?, ?, ?, ?, ?)
                """, GROUP_ID, MEETING_LONGITUDE, MEETING_LATITUDE, CONFIRMED_AT, NOW, NOW);
        jdbc.update("""
                INSERT INTO match_group_members(
                    group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES (?, ?, 'JOINED', true, ?, ?), (?, ?, 'JOINED', true, ?, ?)
                """, GROUP_ID, ME, NOW, NOW, GROUP_ID, PARTNER, NOW, NOW);
        setTemperature(ME, "34.50");
        setTemperature(PARTNER, "34.50");
        // fixture는 두 회원에게 WAITING 풀을 남겨둔다. 그대로 두면 재매칭 신청이 "이미 진행 중인
        // pool" 검사에서 먼저 막혀, 정작 확인하려는 활성 그룹·완료 잠금 검사에 닿지 못한다.
        jdbc.update("UPDATE match_pools SET status = 'CANCELLED', updated_at = ? WHERE member_id IN (?, ?)", NOW, ME, PARTNER);
    }

    @Test
    @DisplayName("전원 도착해도 방은 유지되고, 만남 시간이 끝나야 완료·보상·잠금 해제가 일어난다")
    void 전원_도착부터_만남_종료까지() throws Exception {
        arrive(ME).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        arrive(PARTNER).andExpect(status().isOk())
                // 마지막 도착자에게도 완료가 아니라 진행 중이 보인다.
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());

        // 만남이 진행 중인 동안에는 새 매칭을 신청할 수 없다(활성 그룹 참여).
        enterPool(ME)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_CONFLICT"));
        assertThat(temperature(ME)).isEqualByComparingTo("34.50");

        // 만남 시간이 끝나면 그룹이 닫히고 보상이 지급된다.
        assertThat(meetingCloses.process(GROUP_ID, meetingEndsAt())).isTrue();

        assertThat(groupStatus()).isEqualTo("COMPLETED");
        assertThat(temperature(ME)).isEqualByComparingTo("35.00");
        assertThat(temperature(PARTNER)).isEqualByComparingTo("35.00");
        assertThat(memberStatus(ME)).isEqualTo("COMPLETED");
        // 상태방은 사라지고 매칭 기록에서 완료로 보인다.
        mockMvc.perform(get("/api/matching/groups/me/current").cookie(cookie(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * 방이 닫히는 시각과 재매칭 잠금이 풀리는 시각은 같아야 한다.
     *
     * <p>두 값이 어긋나면 "만남은 끝났는데 신청이 안 되는" 구간이나 "방은 살아 있는데 새 매칭이
     * 잡히는" 구간이 생긴다. 고정 시계로는 두 시각을 동시에 재현할 수 없어 상수로 고정한다.
     */
    @Test
    @DisplayName("만남 시간과 재매칭 잠금 시간은 같은 값이어야 한다")
    void 만남_시간과_잠금_시간이_같다() {
        assertThat(MatchMeetingWindowPolicy.MEETING_WINDOW)
                .isEqualTo(com.survey.meetorsolo.domain.matching.service
                        .MatchCompletionLockPolicy.MATCH_VALIDITY);
    }

    @Test
    @DisplayName("도착 반경 밖에서는 도착이 거절되고, 반경 안이면 거리만 기록된다")
    void 도착_반경_검증() throws Exception {
        arriveWith(ME, FAR_AWAY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_ARRIVAL_OUT_OF_RANGE"));
        assertThat(memberStatus(ME)).isEqualTo("JOINED");

        arrive(ME).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT arrival_distance_meters FROM match_group_members
                WHERE group_id = ? AND member_id = ?
                """, Integer.class, GROUP_ID, ME)).isNotNull().isLessThanOrEqualTo(150);
    }

    @Test
    @DisplayName("만남 성립 전에는 먼저 나가기가 막히고 참여 취소만 열린다")
    void 성립_전에는_취소_경로만() throws Exception {
        arrive(ME).andExpect(status().isOk());

        // 도착 버튼을 눌렀다 무패널티로 나가는 샛길을 막는다.
        mockMvc.perform(put("/api/matching/groups/me/current/leave").cookie(cookie(ME)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_LEAVE_NOT_ALLOWED"));

        // 도착한 뒤에도 참여 취소는 열려 있다. 예전에는 도착과 동시에 막혔다.
        mockMvc.perform(put("/api/matching/groups/me/current/cancellation")
                        .cookie(cookie(ME))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupContinues").value(false));

        assertThat(groupStatus()).isEqualTo("CANCELLED");
        assertThat(temperature(ME)).isEqualByComparingTo("34.50");
    }

    @Test
    @DisplayName("만남 성립 후에는 먼저 나가도 방이 유지되고 나간 사람도 보상을 받는다")
    void 성립_후_먼저_나가기() throws Exception {
        arrive(ME).andExpect(status().isOk());
        arrive(PARTNER).andExpect(status().isOk());

        mockMvc.perform(put("/api/matching/groups/me/current/leave").cookie(cookie(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberStatus").value("LEFT"))
                .andExpect(jsonPath("$.data.groupContinues").value(true));

        // 혼자 남아도 상태방은 열린다.
        assertThat(groupStatus()).isEqualTo("IN_PROGRESS");
        mockMvc.perform(get("/api/matching/groups/me/current").cookie(cookie(PARTNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentMemberCount").value(1));
        // 나간 사람에게는 페널티가 없다.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM match_penalty_events WHERE related_group_id = ?",
                Integer.class, GROUP_ID)).isZero();
        // 나간 사람도 잠금은 그대로다. 만남을 이미 했기 때문이다. 이 조건이 없으면 나간 사람만
        // 잠금을 피해 곧바로 재매칭할 수 있어 끝까지 남은 사람이 손해를 본다.
        mockMvc.perform(get("/api/matching/me/restrictions").cookie(cookie(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completionLock.active").value(true))
                .andExpect(jsonPath("$.data.completionLock.groupId").value((int) GROUP_ID));
        enterPool(ME)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_COMPLETION_LOCKED"));

        assertThat(meetingCloses.process(GROUP_ID, meetingEndsAt())).isTrue();

        // 머문 시간으로 가르지 않는다. 먼저 간 사람도 도착자이므로 보상 대상이다.
        assertThat(groupStatus()).isEqualTo("COMPLETED");
        assertThat(temperature(ME)).isEqualByComparingTo("35.00");
        assertThat(temperature(PARTNER)).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("도착자가 없던 매칭은 신고할 수 없고, 노쇼로 취소된 매칭은 신고할 수 있다")
    void 신고_대상_판정() throws Exception {
        // 아무도 도착하지 않은 채 도착 마감이 지나면 그룹이 취소된다.
        noShows.process(GROUP_ID, CONFIRMED_AT.plusMinutes(31));
        assertThat(groupStatus()).isEqualTo("CANCELLED");

        mockMvc.perform(post("/api/match-groups/{groupId}/reports", GROUP_ID)
                        .cookie(cookie(ME))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedMemberId\":" + PARTNER + ",\"reasonCode\":\"NO_SHOW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_MEETING_NOT_HELD"));

        // 기다린 사람이 도착해 있었다면 같은 취소 건도 신고할 수 있다. 노쇼 신고 경로가
        // 막히지 않는다는 것이 도착 여부를 기준으로 잡은 이유다(docs/19 4.11.1).
        jdbc.update("""
                UPDATE match_group_members SET arrived_at = ?
                WHERE group_id = ? AND member_id = ?
                """, CONFIRMED_AT.plusMinutes(5), GROUP_ID, ME);

        mockMvc.perform(post("/api/match-groups/{groupId}/reports", GROUP_ID)
                        .cookie(cookie(ME))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportedMemberId\":" + PARTNER + ",\"reasonCode\":\"NO_SHOW\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    private org.springframework.test.web.servlet.ResultActions arrive(long memberId)
            throws Exception {
        return arriveWith(memberId, AT_MEETING_POINT);
    }

    private org.springframework.test.web.servlet.ResultActions arriveWith(
            long memberId, String body) throws Exception {
        return mockMvc.perform(put("/api/matching/groups/me/current/arrival")
                .cookie(cookie(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 새 매칭 신청. 제한이 걸려 있으면 여기서 막혀야 한다. */
    private org.springframework.test.web.servlet.ResultActions enterPool(long memberId)
            throws Exception {
        return mockMvc.perform(post("/api/matching/pools")
                .cookie(cookie(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"festivalId":9100001,"preferredGroupSize":2,
                         "allowMinimumTwo":true,"tags":[]}
                        """));
    }

    private OffsetDateTime meetingEndsAt() {
        return CONFIRMED_AT.plus(MatchMeetingWindowPolicy.MEETING_WINDOW);
    }

    private String groupStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM match_groups WHERE id = ?", String.class, GROUP_ID);
    }

    private String memberStatus(long memberId) {
        return jdbc.queryForObject("""
                SELECT status FROM match_group_members WHERE group_id = ? AND member_id = ?
                """, String.class, GROUP_ID, memberId);
    }

    private BigDecimal temperature(long memberId) {
        return jdbc.queryForObject(
                "SELECT manner_temperature FROM members WHERE id = ?", BigDecimal.class, memberId);
    }

    private void setTemperature(long memberId, String value) {
        jdbc.update("UPDATE members SET manner_temperature = ?::numeric WHERE id = ?",
                value, memberId);
    }

    private jakarta.servlet.http.Cookie cookie(long memberId) {
        return new jakarta.servlet.http.Cookie(
                "access_token", jwtProvider.createAccessToken(memberId, "ACTIVE"));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedLifecycleClock() {
            return Clock.fixed(TEST_NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
