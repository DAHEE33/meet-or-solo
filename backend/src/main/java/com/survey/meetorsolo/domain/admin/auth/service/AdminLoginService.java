package com.survey.meetorsolo.domain.admin.auth.service;

import com.survey.meetorsolo.domain.admin.auth.dto.AdminLoginRequest;
import com.survey.meetorsolo.domain.admin.auth.entity.AdminCredential;
import com.survey.meetorsolo.domain.admin.auth.repository.AdminCredentialRepository;
import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.service.AuthService;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슈퍼관리자 ID/PW 로그인({@code docs/30}).
 *
 * <p>SSO를 대체하지 않고 진입 경로만 하나 더 만든다. 검증이 끝나면 OAuth 로그인과 같은
 * {@link AuthService#issueSession}을 호출하므로, 발급되는 session과 그 뒤의 관리자 API는
 * 소셜 로그인과 완전히 동일하다.
 */
@Service
public class AdminLoginService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginService.class);

    /**
     * 아이디가 없을 때도 비교에 쓰는 더미 해시.
     *
     * <p>없는 아이디는 즉시 실패하고 있는 아이디는 BCrypt 비교(수십 ms)를 거치면, 응답
     * 시간만으로 아이디의 존재를 알 수 있다. 실제 해시와 같은 비용을 한 번 치러 그 차이를
     * 없앤다. 이 해시와 일치하는 비밀번호는 알려져 있지 않으며, 결과는 항상 버린다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AdminCredentialRepository credentials;
    private final AdminLoginFailureRecorder failureRecorder;
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final MemberAccessPolicy accessPolicy;
    private final Clock clock;

    public AdminLoginService(
            AdminCredentialRepository credentials,
            AdminLoginFailureRecorder failureRecorder,
            MemberRepository members,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            MemberAccessPolicy accessPolicy,
            Clock clock
    ) {
        this.credentials = credentials;
        this.failureRecorder = failureRecorder;
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    /**
     * 로그인. 어떤 이유로 실패하든 {@link ErrorCode#ADMIN_LOGIN_FAILED} 하나로 응답한다.
     *
     * <p>실패 사유를 구분해 알려주면 아이디 열거와 잠금 여부 탐지가 가능해진다. 운영에서
     * 원인을 봐야 하므로 사유는 로그에만 남기고, 아이디·비밀번호는 로그에 남기지 않는다.
     */
    @Transactional
    public AuthTokenResponse login(AdminLoginRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<AdminCredential> found = credentials.findByUsername(request.username());
        if (found.isEmpty()) {
            // 비교 비용을 똑같이 치른 뒤 실패시킨다. 결과는 쓰지 않는다.
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw failure("unknown username");
        }

        AdminCredential credential = found.get();
        if (credential.isLocked(now)) {
            throw failure("locked");
        }
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw recordAndFail(credential, now, "password mismatch");
        }

        Member member = members.findById(credential.getMemberId())
                .orElseThrow(() -> failure("member missing"));
        // 토큰의 role이 아니라 DB의 role을 본다. 자격증명이 남아 있어도 권한을 회수하면
        // 그 즉시 로그인이 막혀야 한다.
        if (!Member.ROLE_ADMIN.equals(member.getRole())) {
            throw recordAndFail(credential, now, "not an admin");
        }
        requireAccessible(member, credential, now);

        credential.recordSuccess(now);
        member.markLoggedIn();
        return authService.issueSession(member);
    }

    /**
     * 제재 회원은 관리자여도 로그인시키지 않는다.
     *
     * <p>{@code requireAccessible}은 정지까지 막는다. 소셜 로그인은 정지 회원도 통과시키지만
     * (조회는 계속할 수 있어야 한다) 여기는 관리자 콘솔 진입 전용 경로라 같은 예외를 둘 이유가
     * 없다. {@code AdminAuthorizationService.requireAdmin}도 같은 판정을 쓴다.
     */
    private void requireAccessible(Member member, AdminCredential credential, OffsetDateTime now) {
        try {
            accessPolicy.requireAccessible(member);
        } catch (BusinessException exception) {
            throw recordAndFail(credential, now, "member not accessible: " + exception.getErrorCode());
        }
    }

    /**
     * 실패 횟수를 기록하고 실패를 알린다.
     *
     * <p>기록을 {@link AdminLoginFailureRecorder}에 맡기는 이유는 곧바로 던질 예외가 이
     * 메서드의 transaction을 rollback시키기 때문이다. 같은 transaction에서 올리면 UPDATE가
     * 함께 사라져 잠금이 영원히 걸리지 않는다.
     */
    private BusinessException recordAndFail(AdminCredential credential, OffsetDateTime now, String reason) {
        failureRecorder.record(credential, now);
        return failure(reason);
    }

    private BusinessException failure(String reason) {
        log.warn("관리자 ID/PW 로그인 실패: {}", reason);
        return new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED);
    }
}
