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
        "app.jwt.secret=matching-rest-api-integration-test-secret"
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

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedMatchingRestClock() {
            return Clock.fixed(NOW.plusSeconds(10).toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
