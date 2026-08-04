package com.survey.meetorsolo.domain.matching.controller;

import static com.survey.meetorsolo.domain.matching.fixture.MatchingScenarioFixture.NOW;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=matching-rest-api-integration-test-secret",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(MatchingRestApiIntegrationTest.FixedClockConfiguration.class)
@Sql(
        scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class MatchingRestApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpRoundTwoAttemptMembers() {
        jdbc.update("""
                INSERT INTO match_attempt_members(
                    attempt_id, member_id, pool_id, member_score, status, created_at, updated_at
                ) VALUES
                    (9130001, 9110001, 9120001, 80.00, 'ACCEPTED', ?, ?),
                    (9130001, 9110002, 9120002, 80.00, 'ACCEPTED', ?, ?)
                """, NOW, NOW, NOW, NOW);
        jdbc.update("""
                UPDATE match_pools
                SET status = 'PROPOSED', updated_at = ?
                WHERE id IN (9120001, 9120002)
                """, NOW);
        jdbc.update("""
                UPDATE match_proposals
                SET status = 'ACCEPTED', responded_at = ?, updated_at = ?
                WHERE id = 9140006
                """, NOW.plusSeconds(5), NOW.plusSeconds(5));
        jdbc.update("""
                INSERT INTO match_responses(
                    proposal_id, attempt_id, member_id, response, responded_at, created_at
                ) VALUES (9140006, 9130001, 9110002, 'START_WITH_CURRENT_MEMBERS', ?, ?)
                """, NOW.plusSeconds(5), NOW.plusSeconds(5));
    }

    @Test
    void JWT_회원의_active_round2_proposal을_조회한다() throws Exception {
        mockMvc.perform(get("/api/matching/proposals/me/active")
                        .cookie(cookie(9_110_001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposalId").value(9_140_005))
                .andExpect(jsonPath("$.data.proposalType")
                        .value("INSUFFICIENT_MEMBERS_CONFIRMATION"))
                .andExpect(jsonPath("$.data.proposalRound").value(2));
    }

    @Test
    void 다른_회원의_proposal에_응답하면_404로_존재를_숨긴다() throws Exception {
        mockMvc.perform(post("/api/matching/proposals/9140005/responses")
                        .cookie(cookie(9_110_006L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MATCHING_RESOURCE_NOT_FOUND"));
    }

    @Test
    void round2_ACCEPT는_START_WITH_CURRENT_MEMBERS이며_동일_요청은_멱등하다() throws Exception {
        mockMvc.perform(post("/api/matching/proposals/9140005/responses")
                        .cookie(cookie(9_110_001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordedResponse")
                        .value("START_WITH_CURRENT_MEMBERS"))
                .andExpect(jsonPath("$.data.attemptStatus").value("CONFIRMED"));

        mockMvc.perform(post("/api/matching/proposals/9140005/responses")
                        .cookie(cookie(9_110_001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordedResponse")
                        .value("START_WITH_CURRENT_MEMBERS"))
                .andExpect(jsonPath("$.data.attemptStatus").value("CONFIRMED"));

        mockMvc.perform(post("/api/matching/proposals/9140005/responses")
                        .cookie(cookie(9_110_001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CANCEL_CURRENT_MEMBERS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_CONFLICT"));

        Long groupId = jdbc.queryForObject(
                "SELECT id FROM match_groups WHERE attempt_id = 9130001",
                Long.class
        );
        for (long memberId : new long[]{9_110_001L, 9_110_002L}) {
            mockMvc.perform(get("/api/matching/groups/me/current")
                            .cookie(cookie(memberId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groupId").value(groupId))
                    .andExpect(jsonPath("$.data.confirmedMemberCount").value(2))
                    .andExpect(jsonPath("$.data.members.length()").value(2))
                    .andExpect(jsonPath("$.data.members[0].memberId").value(9_110_001))
                    .andExpect(jsonPath("$.data.members[1].memberId").value(9_110_002));
        }
        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_006L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void CONFIRMED_group은_참여자에게만_동일한_최종_계약으로_노출된다() throws Exception {
        jdbc.update("""
                UPDATE members
                SET profile_image_url = CASE
                    WHEN id = 9110001 THEN NULL
                    ELSE 'https://example.com/member-2.png'
                END
                WHERE id IN (9110001, 9110002)
                """);
        insertGroup("CONFIRMED");

        for (long memberId : new long[]{9_110_001L, 9_110_002L}) {
            mockMvc.perform(get("/api/matching/groups/me/current")
                            .cookie(cookie(memberId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.groupId").value(9_170_001))
                    .andExpect(jsonPath("$.data.festivalId").value(9_100_001))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data.confirmedMemberCount").value(2))
                    .andExpect(jsonPath("$.data.confirmedAt")
                            .value("2026-07-17T15:00:10+09:00"))
                    .andExpect(jsonPath("$.data.arrivalDeadlineAt")
                            .value("2026-07-17T15:30:10+09:00"))
                    .andExpect(jsonPath("$.data.festival.festivalId").value(9_100_001))
                    .andExpect(jsonPath("$.data.festival.title").value("매칭 테스트 강원 축제"))
                    .andExpect(jsonPath("$.data.festival.address").doesNotExist())
                    .andExpect(jsonPath("$.data.festival.eventStartDate").value("2026-07-01"))
                    .andExpect(jsonPath("$.data.festival.eventEndDate").value("2026-07-31"))
                    .andExpect(jsonPath("$.data.members[0].memberId").value(9_110_001))
                    .andExpect(jsonPath("$.data.members[0].nickname")
                            .value("fixture9110001"))
                    .andExpect(jsonPath("$.data.members[0].profileImageUrl").doesNotExist())
                    .andExpect(jsonPath("$.data.members[0].status").value("JOINED"))
                    .andExpect(jsonPath("$.data.members[1].memberId").value(9_110_002))
                    .andExpect(jsonPath("$.data.members[1].profileImageUrl")
                            .value("https://example.com/member-2.png"))
                    .andExpect(jsonPath("$.data.members[1].status").value("JOINED"));
        }

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_006L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void IN_PROGRESS_group도_current_API에서_반환한다() throws Exception {
        insertGroup("IN_PROGRESS");

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.festival.title").value("매칭 테스트 강원 축제"))
                .andExpect(jsonPath("$.data.members.length()").value(2));
    }

    @Test
    void inactive_member는_제외하고_확정_인원과_불일치하면_충돌로_처리한다() throws Exception {
        insertGroup("CONFIRMED");
        jdbc.update("""
                UPDATE match_group_members
                SET status = 'LEFT', updated_at = ?
                WHERE id = 9180002
                """, NOW.plusSeconds(11));

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_001L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_CONFLICT"));

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_002L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 최초_확정_3명에서_active_2명이_남으면_두_count를_정상_반환한다() throws Exception {
        insertGroup("CONFIRMED");
        jdbc.update("""
                UPDATE match_groups
                SET confirmed_member_count = 3, updated_at = ?
                WHERE id = 9170001
                """, NOW.plusSeconds(11));

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedMemberCount").value(3))
                .andExpect(jsonPath("$.data.currentMemberCount").value(2))
                .andExpect(jsonPath("$.data.members.length()").value(2));
    }

    @Test
    void active_member가_최초_확정_인원보다_많으면_충돌로_처리한다() throws Exception {
        insertGroup("CONFIRMED");
        jdbc.update("""
                INSERT INTO match_group_members(
                    id, group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES (9180003, 9170001, 9110003, 'JOINED', true, ?, ?)
                """, NOW, NOW);

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_001L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_CONFLICT"));
    }

    @Test
    void COMPLETED_group은_current_API에서_제외한다() throws Exception {
        insertGroup("COMPLETED");

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void CANCELLED_group은_current_API에서_제외한다() throws Exception {
        insertGroup("CANCELLED");

        mockMvc.perform(get("/api/matching/groups/me/current")
                        .cookie(cookie(9_110_001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void round2_REJECT는_허용되지_않아_400이다() throws Exception {
        mockMvc.perform(post("/api/matching/proposals/9140005/responses")
                        .cookie(cookie(9_110_001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MATCHING_INVALID_REQUEST"));
    }

    private jakarta.servlet.http.Cookie cookie(long memberId) {
        return new jakarta.servlet.http.Cookie(
                "access_token",
                jwtProvider.createAccessToken(memberId, "ACTIVE")
        );
    }

    private void insertGroup(String status) {
        jdbc.update("""
                INSERT INTO match_groups(
                    id, attempt_id, festival_id, status, confirmed_member_count,
                    confirmed_at, created_at, updated_at
                ) VALUES (9170001, 9130001, 9100001, ?, 2, ?, ?, ?)
                """, status, NOW.plusSeconds(10), NOW.plusSeconds(10), NOW.plusSeconds(10));
        jdbc.update("""
                INSERT INTO match_group_members(
                    id, group_id, member_id, status, allow_minimum_two, created_at, updated_at
                ) VALUES
                    (9180002, 9170001, 9110002, 'JOINED', true, ?, ?),
                    (9180001, 9170001, 9110001, 'JOINED', true, ?, ?)
                """, NOW, NOW, NOW, NOW);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedMatchingRestClock() {
            return Clock.fixed(NOW.plusSeconds(10).toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
