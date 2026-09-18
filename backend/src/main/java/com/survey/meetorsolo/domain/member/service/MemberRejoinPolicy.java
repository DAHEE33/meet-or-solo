package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.global.error.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 탈퇴 회원이 같은 소셜 계정으로 다시 로그인했을 때의 판정({@code docs/19} 4.4).
 *
 * <table>
 *   <caption>재가입 판정</caption>
 *   <tr><th>탈퇴 경로</th><th>쿨오프 내</th><th>쿨오프 경과</th></tr>
 *   <tr><td>본인 탈퇴</td><td>거부 + 재가입 가능 시각 안내</td><td>계정 부활</td></tr>
 *   <tr><td>관리자 강제 탈퇴({@code blockRejoin})</td><td>거부</td><td><b>영구 거부</b></td></tr>
 * </table>
 *
 * <p>거부는 {@link MemberSanctionException}으로 던진다. 제재는 아니지만
 * {@code AuthController}의 OAuth callback과 {@code GlobalExceptionHandler}가 이미 그 예외를
 * 잡아 안내 cookie와 {@code 403} body를 만들어 주므로, 두 곳을 고치지 않고 안내 경로를
 * 재사용할 수 있다. 새 경로를 만들면 302 redirect에 body가 없어 4.8이 고친
 * "소셜 로그인에 실패했습니다" 오안내가 재발한다.
 */
@Service
public class MemberRejoinPolicy {

    private final Clock clock;
    private final String supportContactEmail;

    public MemberRejoinPolicy(
            Clock clock,
            @Value("${app.support.contact-email:}") String supportContactEmail
    ) {
        this.clock = clock;
        this.supportContactEmail = supportContactEmail;
    }

    /**
     * 탈퇴 회원이면 재가입 판정을 하고, 통과하면 계정을 되살린다.
     * 탈퇴 회원이 아니면 아무것도 하지 않는다.
     *
     * <p>되살리기 전에 반드시 거부 판정을 끝낸다. 순서가 바뀌면 거부해야 하는 회원의
     * 익명화된 프로필이 OAuth 응답으로 다시 채워진다.
     *
     * @return 실제로 재가입 처리를 했으면 {@code true}
     * @throws MemberSanctionException 재가입이 막힌 경우
     */
    public boolean rejoinIfWithdrawn(Member member) {
        if (!Member.STATUS_WITHDRAWN.equals(member.getStatus())) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (member.isRejoinBlocked()) {
            throw blocked(member);
        }
        if (now.isBefore(MemberRejoinCooldownPolicy.rejoinAvailableAt(member.getWithdrawnAt()))) {
            throw blocked(member);
        }
        member.rejoin(now);
        return true;
    }

    /**
     * 탈퇴 회원의 재가입 안내. 탈퇴 회원이 아니면 {@code null}이다.
     *
     * <p><b>로그인 화면이 실제로 읽는 값이다.</b> OAuth callback은 302 redirect라 body가 없어
     * 예외에 실은 안내가 화면에 도달하지 못한다. 화면은 {@code GET /api/auth/sanction-notice}로
     * 다시 조회하고, 그 경로가 {@link MemberAccessPolicy#sanctionNoticeOf}를 통해 이 메서드에
     *닿는다. <b>여기서 {@code null}을 주면 로그인 화면이 제재용 포괄 문구로 떨어져 탈퇴한
     * 사용자에게 "계정이 제재되어"라는 틀린 안내가 뜬다.</b>
     */
    public MemberSanctionNotice noticeFor(Member member) {
        if (!Member.STATUS_WITHDRAWN.equals(member.getStatus())) {
            return null;
        }
        return MemberSanctionNotice.forWithdrawn(rejoinAvailableAt(member), supportContactEmail);
    }

    /** 재가입 가능 시각. 영구 거부이면 {@code null}이라 화면이 대기 안내를 띄우지 않는다. */
    private OffsetDateTime rejoinAvailableAt(Member member) {
        return member.isRejoinBlocked()
                ? null
                : MemberRejoinCooldownPolicy.rejoinAvailableAt(member.getWithdrawnAt());
    }

    private MemberSanctionException blocked(Member member) {
        return new MemberSanctionException(
                ErrorCode.MEMBER_REJOIN_BLOCKED, member.getId(), noticeFor(member));
    }
}
