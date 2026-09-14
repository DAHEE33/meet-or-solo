package com.survey.meetorsolo.domain.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.admin.auth.dto.AdminLoginRequest;
import com.survey.meetorsolo.domain.admin.auth.entity.AdminCredential;
import com.survey.meetorsolo.domain.admin.auth.repository.AdminCredentialRepository;
import com.survey.meetorsolo.domain.admin.auth.service.AdminLoginFailureRecorder;
import com.survey.meetorsolo.domain.admin.auth.service.AdminLoginService;
import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.service.AuthService;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminLoginServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-14T12:00:00+09:00");
    private static final String PASSWORD = "super-secret-password";

    private final AdminCredentialRepository credentials = mock(AdminCredentialRepository.class);
    private final AdminLoginFailureRecorder failureRecorder = mock(AdminLoginFailureRecorder.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuthService authService = mock(AuthService.class);
    private final MemberAccessPolicy accessPolicy = mock(MemberAccessPolicy.class);
    private final Clock clock = Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"));

    private final AdminLoginService service = new AdminLoginService(
            credentials, failureRecorder, members, passwordEncoder, authService, accessPolicy, clock);

    private AdminCredential credential;
    private Member admin;

    @BeforeEach
    void prepare() {
        credential = AdminCredential.issue(7L, "admin", passwordEncoder.encode(PASSWORD));
        ReflectionTestUtils.setField(credential, "id", 1L);
        admin = Member.createLocalAdmin("admin", "슈퍼관리자");
        ReflectionTestUtils.setField(admin, "id", 7L);
    }

    private AdminLoginRequest request(String password) {
        return new AdminLoginRequest("admin", password);
    }

    private void existingCredential() {
        when(credentials.findByUsername("admin")).thenReturn(Optional.of(credential));
        when(members.findById(7L)).thenReturn(Optional.of(admin));
    }

    private AuthTokenResponse tokens() {
        return new AuthTokenResponse("Bearer", "access", "refresh", 1800, 20160, 7L, "ACTIVE");
    }

    private void lockCredential(OffsetDateTime at) {
        for (int attempt = 0; attempt < AdminCredential.MAX_FAILED_ATTEMPTS; attempt++) {
            credential.recordFailure(at);
        }
    }

    @Test
    void 올바른_자격증명이면_session을_발급하고_실패_누적을_초기화한다() {
        existingCredential();
        AuthTokenResponse expected = tokens();
        when(authService.issueSession(admin)).thenReturn(expected);
        credential.recordFailure(NOW);

        assertThat(service.login(request(PASSWORD))).isSameAs(expected);
        assertThat(credential.getFailedAttempts()).isZero();
        assertThat(credential.getLastLoginAt()).isEqualTo(NOW);
        assertThat(admin.getLastLoginAt()).isNotNull();
    }

    @Test
    void 비밀번호가_틀리면_ADMIN_LOGIN_FAILED이고_실패를_별도_transaction으로_기록한다() {
        existingCredential();

        assertThatThrownBy(() -> service.login(request("wrong-password")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED));
        verify(failureRecorder).record(credential, NOW);
        verify(authService, never()).issueSession(any());
    }

    /** 없는 아이디와 틀린 비밀번호가 같은 응답이어야 아이디를 열거할 수 없다. */
    @Test
    void 없는_아이디도_같은_ADMIN_LOGIN_FAILED로_응답한다() {
        when(credentials.findByUsername("admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request(PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED));
    }

    @Test
    void 잠긴_계정은_올바른_비밀번호여도_실패한다() {
        existingCredential();
        lockCredential(NOW);
        assertThat(credential.isLocked(NOW)).isTrue();

        assertThatThrownBy(() -> service.login(request(PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED));
        verify(authService, never()).issueSession(any());
    }

    @Test
    void 잠금_시간이_지나면_다시_로그인할_수_있다() {
        existingCredential();
        lockCredential(NOW.minus(AdminCredential.LOCK_DURATION).minusMinutes(1));
        when(authService.issueSession(admin)).thenReturn(tokens());

        assertThat(service.login(request(PASSWORD))).isNotNull();
    }

    /** 자격증명이 남아 있어도 role을 회수하면 그 즉시 막혀야 한다. */
    @Test
    void 관리자_권한이_없는_계정은_로그인할_수_없다() {
        Member user = Member.createKakaoMember("kakao-1", "회원", null);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(credentials.findByUsername("admin")).thenReturn(Optional.of(credential));
        when(members.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(request(PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED));
        verify(failureRecorder).record(credential, NOW);
        verify(authService, never()).issueSession(any());
    }

    @Test
    void 제재된_관리자는_로그인할_수_없다() {
        existingCredential();
        doThrow(new BusinessException(ErrorCode.MEMBER_SUSPENDED))
                .when(accessPolicy).requireAccessible(admin);

        assertThatThrownBy(() -> service.login(request(PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_LOGIN_FAILED));
        verify(failureRecorder).record(credential, NOW);
        verify(authService, never()).issueSession(any());
    }

    /** 실패가 누적돼 잠기면 카운터를 0으로 되돌린다. 되돌리지 않으면 사실상 영구 잠금이 된다. */
    @Test
    void 잠긴_뒤에는_실패_카운터가_초기화된다() {
        lockCredential(NOW);

        assertThat(credential.getFailedAttempts()).isZero();
        assertThat(credential.getLockedUntil()).isEqualTo(NOW.plus(AdminCredential.LOCK_DURATION));
        assertThat(credential.isLocked(NOW.plus(AdminCredential.LOCK_DURATION))).isFalse();
    }

    @Test
    void 비밀번호_회전은_잠금과_실패_누적을_함께_푼다() {
        lockCredential(NOW);
        credential.rotatePassword(passwordEncoder.encode("new-password"));

        assertThat(credential.isLocked(NOW)).isFalse();
        assertThat(credential.getFailedAttempts()).isZero();
    }
}
