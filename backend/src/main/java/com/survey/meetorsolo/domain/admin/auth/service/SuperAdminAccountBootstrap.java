package com.survey.meetorsolo.domain.admin.auth.service;

import com.survey.meetorsolo.domain.admin.auth.entity.AdminCredential;
import com.survey.meetorsolo.domain.admin.auth.repository.AdminCredentialRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슈퍼관리자 계정을 환경변수로 만들고 비밀번호를 회전한다({@code docs/30}).
 *
 * <p>계정을 migration에 넣지 않는 이유는 두 가지다. 해시라도 저장소에 커밋되면
 * {@code docs/06}의 Secret 규칙 위반이고, 계정을 바꿀 때마다 migration을 추가해야 한다.
 *
 * <p><b>비밀번호 변경도 이 경로다.</b> 환경변수를 바꾸고 재기동하면 해시가 갱신된다.
 * 별도의 비밀번호 변경 화면을 만들지 않은 이유가 이것이다.
 *
 * <p>환경변수가 하나라도 비어 있으면 아무것도 하지 않는다. 기본 계정을 자동으로 만들면
 * 운영에 예측 가능한 아이디·비밀번호가 생긴다.
 */
@Component
public class SuperAdminAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminAccountBootstrap.class);

    private final MemberRepository members;
    private final AdminCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String nickname;

    public SuperAdminAccountBootstrap(
            MemberRepository members,
            AdminCredentialRepository credentials,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.local.username:}") String username,
            @Value("${app.admin.local.password:}") String password,
            @Value("${app.admin.local.nickname:슈퍼관리자}") String nickname
    ) {
        this.members = members;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.nickname = nickname;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            log.info("슈퍼관리자 로컬 계정 설정이 없어 생성을 건너뜁니다.");
            return;
        }

        // provider_user_id를 username과 같은 값으로 둔다. (provider, provider_user_id)가
        // UNIQUE이므로 이 조회 하나로 "이 아이디의 계정이 이미 있는지"가 정해진다.
        Member member = members.findByProviderAndProviderUserId(Member.PROVIDER_LOCAL, username)
                .orElseGet(() -> members.save(Member.createLocalAdmin(username, nickname)));

        // 권한을 되살린다. 운영 중 실수로 role이 내려갔을 때 재기동만으로 복구할 수 있어야 한다.
        member.restoreLocalAdminRole();

        credentials.findByMemberId(member.getId())
                .ifPresentOrElse(
                        credential -> rotateIfChanged(credential),
                        () -> credentials.save(AdminCredential.issue(
                                member.getId(), username, passwordEncoder.encode(password))));
        log.info("슈퍼관리자 로컬 계정을 확인했습니다. memberId={}", member.getId());
    }

    /**
     * 이미 같은 비밀번호면 해시를 다시 만들지 않는다.
     *
     * <p>BCrypt는 salt가 매번 달라 같은 비밀번호도 해시 문자열이 달라진다. 무조건 덮으면
     * 재기동할 때마다 {@code password_hash}와 {@code updated_at}이 바뀌어, 감사할 때 실제
     * 비밀번호 변경과 단순 재기동을 구분할 수 없다. 회전은 잠금과 실패 누적도 함께 푼다.
     */
    private void rotateIfChanged(AdminCredential credential) {
        if (passwordEncoder.matches(password, credential.getPasswordHash())) {
            return;
        }
        credential.rotatePassword(passwordEncoder.encode(password));
        log.info("슈퍼관리자 로컬 계정의 비밀번호를 갱신했습니다.");
    }
}
