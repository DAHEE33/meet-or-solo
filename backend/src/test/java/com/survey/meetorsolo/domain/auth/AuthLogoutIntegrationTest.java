package com.survey.meetorsolo.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.entity.RefreshToken;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.repository.RefreshTokenRepository;
import com.survey.meetorsolo.domain.auth.service.AuthService;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.config.WebSocketSessionRegistry;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * docs/19 4.6 로그아웃의 refresh token 폐기, 멱등성, WebSocket session 종료를 실제 PostgreSQL로 검증한다.
 * access token은 stateless JWT라 서버가 강제 무효화하지 않는다. 브라우저는 cookie가 만료돼 401이 되고,
 * 이미 유출된 raw token은 남은 만료 시간까지 유효하다는 한계는 docs/06_SECURITY_POLICY.md에 기록했다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.jwt.secret=auth-logout-integration-test-secret",
        "app.admin.report.cursor-hmac-secret=auth-logout-cursor-test-secret-32-bytes",
        "app.admin.member.suspension-scheduler-enabled=false",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
class AuthLogoutIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired AuthService authService;
    @Autowired JwtProvider jwtProvider;
    @Autowired MemberRepository members;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired WebSocketSessionRegistry sessions;
    @Autowired JdbcTemplate jdbc;

    private Member member;
    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void prepare() {
        jdbc.update("TRUNCATE TABLE members RESTART IDENTITY CASCADE");
        member = members.save(Member.createKakaoMember("auth-logout-user", "로그아웃", null));
        accessToken = jwtProvider.createAccessToken(member);
        refreshToken = jwtProvider.createRefreshToken(member);
        refreshTokens.save(RefreshToken.issue(
                member, jwtProvider.hashToken(refreshToken), SeoulDateTime.now().plusDays(1)));
    }

    @Test
    void 로그아웃하면_refresh_token이_폐기되어_재발급이_거절된다() {
        authService.logout(accessToken);

        assertThat(revokedAt()).isNotNull();
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void 이미_로그아웃된_상태에서_다시_호출해도_멱등하다() {
        authService.logout(accessToken);
        OffsetDateTime firstRevokedAt = revokedAt();

        assertThatCode(() -> authService.logout(accessToken)).doesNotThrowAnyException();

        assertThat(revokedAt()).isEqualTo(firstRevokedAt);
    }

    @Test
    void 토큰이_없거나_변조되면_다른_회원의_session을_폐기하지_않는다() {
        authService.logout(null);
        authService.logout("not-a-jwt");

        assertThat(revokedAt()).isNull();
        assertThatCode(() -> authService.refresh(refreshToken)).doesNotThrowAnyException();
    }

    @Test
    void 로그아웃은_commit_이후_WebSocket_session을_종료한다() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        sessions.register(member.getId(), session);

        authService.logout(accessToken);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    private OffsetDateTime revokedAt() {
        return jdbc.queryForObject(
                "SELECT revoked_at FROM refresh_tokens WHERE member_id = ?",
                OffsetDateTime.class, member.getId());
    }
}
