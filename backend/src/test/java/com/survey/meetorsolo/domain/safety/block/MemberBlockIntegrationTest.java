package com.survey.meetorsolo.domain.safety.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.block.repository.MemberBlockRepository;
import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
        "app.jwt.secret=member-block-integration-test-secret",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class MemberBlockIntegrationTest {
    private static final long ME = 9_110_001L;
    private static final long BLOCKED = 9_110_006L;
    private static final long REVERSE_BLOCKER = 9_110_010L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberBlockRepository repository;

    @BeforeEach
    void profile() {
        jdbc.update("UPDATE members SET nickname='차단상대', profile_image_url='https://img/6' WHERE id=?", BLOCKED);
    }

    @Test
    void 본인_목록만_결정적으로_정렬하고_공개필드만_반환한다() throws Exception {
        OffsetDateTime same = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");
        jdbc.update("INSERT INTO user_blocks(blocker_member_id,blocked_member_id,reason,created_at) VALUES (?,?,?,?)",
                ME, 9_110_002L, "SECRET", same);
        jdbc.update("INSERT INTO user_blocks(blocker_member_id,blocked_member_id,reason,created_at) VALUES (?,?,?,?)",
                ME, 9_110_003L, "SECRET", same);

        mockMvc.perform(get("/api/members/me/blocks").cookie(cookie(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].blockedMemberId").value(9_110_003L))
                .andExpect(jsonPath("$.data[1].blockedMemberId").value(9_110_002L))
                .andExpect(jsonPath("$.data[2].blockedMemberId").value(BLOCKED))
                .andExpect(jsonPath("$.data[2].nickname").value("차단상대"))
                .andExpect(jsonPath("$.data[2].profileImageUrl").value("https://img/6"))
                .andExpect(jsonPath("$.data[2].blockedAt").exists())
                .andExpect(jsonPath("$.data[0].blockerMemberId").doesNotExist())
                .andExpect(jsonPath("$.data[0].blockId").doesNotExist())
                .andExpect(jsonPath("$.data[0].reason").doesNotExist());

        Set<String> fields = Arrays.stream(
                        com.survey.meetorsolo.domain.safety.block.dto.MemberBlockResponse.class
                                .getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder(
                "blockedMemberId", "nickname", "profileImageUrl", "blockedAt");
    }

    @Test
    void 역방향과_다른회원_관계는_노출하지_않고_빈목록은_빈배열이다() throws Exception {
        mockMvc.perform(get("/api/members/me/blocks").cookie(cookie(9_110_004L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/members/me/blocks").cookie(cookie(BLOCKED)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        assertThat(repository.findAllByBlockerMemberId(ME)).hasSize(1);
    }

    @Test
    void 목록과_해제는_미인증을_거절한다() throws Exception {
        mockMvc.perform(get("/api/members/me/blocks"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/members/me/blocks/{id}", BLOCKED))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 정상_해제와_반복_해제는_모두_body없는_204다() throws Exception {
        mockMvc.perform(delete("/api/members/me/blocks/{id}", BLOCKED).cookie(cookie(ME)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mockMvc.perform(delete("/api/members/me/blocks/{id}", BLOCKED).cookie(cookie(ME)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(blockExists(ME, BLOCKED)).isFalse();
    }

    @Test
    void 다른회원과_역방향_row는_삭제하지_않고_부수상태도_변경하지_않는다() throws Exception {
        Snapshot before = snapshot();
        mockMvc.perform(delete("/api/members/me/blocks/{id}", ME).cookie(cookie(BLOCKED)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mockMvc.perform(delete("/api/members/me/blocks/{id}", ME).cookie(cookie(9_110_004L)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(blockExists(ME, BLOCKED)).isTrue();
        assertThat(blockExists(REVERSE_BLOCKER, ME)).isTrue();
        assertThat(snapshot()).isEqualTo(before);
    }

    private boolean blockExists(long blocker, long blocked) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM user_blocks
                WHERE blocker_member_id=? AND blocked_member_id=?)
                """, Boolean.class, blocker, blocked));
    }

    private Snapshot snapshot() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM match_penalty_events) penalties,
                       (SELECT count(*) FROM match_cooldowns) cooldowns,
                       (SELECT count(*) FROM match_events) events,
                       (SELECT count(*) FROM match_group_members) group_members,
                       (SELECT sum(penalty_score) FROM members) penalty_score
                """, (rs, rowNum) -> new Snapshot(rs.getInt("penalties"), rs.getInt("cooldowns"),
                rs.getInt("events"), rs.getInt("group_members"), rs.getInt("penalty_score")));
    }

    private jakarta.servlet.http.Cookie cookie(long memberId) {
        return new jakarta.servlet.http.Cookie(
                "access_token", jwtProvider.createAccessToken(memberId, "ACTIVE"));
    }

    private record Snapshot(int penalties, int cooldowns, int events,
                            int groupMembers, int penaltyScore) {
    }
}
