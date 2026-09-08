package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제재 상태에 따른 접근 허용 범위.
 *
 * <p>목적이 다른 판정 세 개를 제공한다. 정지({@code SUSPENDED})의 허용 범위가 경로마다
 * 다르기 때문이다({@code docs/19} 4.8, {@code docs/06_SECURITY_POLICY.md}).
 *
 * <table>
 *   <caption>판정별 제재 회원 취급</caption>
 *   <tr><th>메서드</th><th>쓰이는 곳</th><th>SUSPENDED</th><th>BANNED</th></tr>
 *   <tr><td>{@code requireSignedIn}</td><td>로그인·token 갱신</td><td>허용</td><td>차단</td></tr>
 *   <tr><td>{@code requireBrowsable}</td><td>조회 요청</td><td>허용</td><td>차단</td></tr>
 *   <tr><td>{@code requireAccessible}</td><td>활동 요청·관리자·WebSocket</td><td>차단</td><td>차단</td></tr>
 * </table>
 *
 * <p>정지는 "조회는 되지만 참여는 안 되는" 상태다. 로그인해서 축제와 코스를 볼 수 있고
 * 체크인·동행 매칭·댓글 작성 같은 활동만 막힌다. 영구 제한은 로그인 자체가 막힌다.
 * 어떤 요청이 활동인지는 {@link SuspendedActivityPolicy}가 정한다.
 */
@Service
public class MemberAccessPolicy {

    private final MemberRepository members;
    private final Clock clock;
    private final String supportContactEmail;

    public MemberAccessPolicy(
            MemberRepository members,
            Clock clock,
            @Value("${app.support.contact-email:}") String supportContactEmail
    ) {
        this.members = members;
        this.clock = clock;
        this.supportContactEmail = supportContactEmail;
    }

    /**
     * 활동 요청에 필요한 판정. 정지 회원을 차단한다.
     *
     * <p>관리자 기능과 WebSocket 연결도 이 판정을 쓴다. 관리자 권한을 정지 중에 유지할 이유가
     * 없고, STOMP는 매칭 상태 동기화 전용이라 활동에 준한다.
     */
    @Transactional
    public Member requireAccessible(long memberId) {
        Member member = lockAndRestore(memberId);
        requireAccessible(member);
        return member;
    }

    public void requireAccessible(Member member) {
        switch (member.getStatus()) {
            case Member.STATUS_ACTIVE, Member.STATUS_PROFILE_REQUIRED -> { }
            case Member.STATUS_SUSPENDED -> throw sanctionException(ErrorCode.MEMBER_SUSPENDED, member);
            case Member.STATUS_BANNED -> throw sanctionException(ErrorCode.MEMBER_BANNED, member);
            default -> throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
        }
    }

    /**
     * 조회 요청에 필요한 판정. 정지 회원을 통과시킨다.
     *
     * <p>정지 회원도 축제·관광지·매칭 기록을 볼 수 있어야 한다. 전면 차단하면 자기 제재
     * 상태와 남은 기간조차 확인할 수 없다.
     */
    @Transactional
    public Member requireBrowsable(long memberId) {
        Member member = lockAndRestore(memberId);
        requireBrowsable(member);
        return member;
    }

    public void requireBrowsable(Member member) {
        switch (member.getStatus()) {
            case Member.STATUS_ACTIVE, Member.STATUS_PROFILE_REQUIRED, Member.STATUS_SUSPENDED -> { }
            case Member.STATUS_BANNED -> throw sanctionException(ErrorCode.MEMBER_BANNED, member);
            default -> throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
        }
    }

    /**
     * 로그인·token 갱신에 필요한 판정. 정지 회원을 통과시킨다.
     *
     * <p>판정 결과는 {@code requireBrowsable}과 같지만 호출 의도가 다르다. 로그인 경로가
     * 조회 판정을 쓰는 것처럼 읽히면 나중에 조회 정책만 손볼 때 로그인이 함께 흔들린다.
     */
    public void requireSignedIn(Member member) {
        requireBrowsable(member);
    }

    /**
     * 제재 중인 회원의 사유·기간 안내를 만든다.
     * 제재 상태가 아니면 {@code null}을 반환하므로 notice 조회 API가 그대로 "안내 없음"으로 응답한다.
     */
    @Transactional
    public MemberSanctionNotice findSanctionNotice(long memberId) {
        return sanctionNoticeOf(lockAndRestore(memberId));
    }

    /**
     * 이미 읽어온 회원의 제재 안내를 만든다. 제재 중이 아니면 {@code null}이다.
     *
     * <p>프로필 조회가 이 메서드를 써서 안내를 함께 내려준다. 그러면 화면이 활동을 시도해
     * {@code 403}을 받기 전에 미리 제재 상태를 알고 UI를 막을 수 있다.
     */
    public MemberSanctionNotice sanctionNoticeOf(Member member) {
        if (!Member.STATUS_SUSPENDED.equals(member.getStatus())
                && !Member.STATUS_BANNED.equals(member.getStatus())) {
            return null;
        }
        return MemberSanctionNotice.of(member, supportContactEmail);
    }

    private Member lockAndRestore(long memberId) {
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        member.restoreExpiredSuspension(OffsetDateTime.now(clock));
        return member;
    }

    private MemberSanctionException sanctionException(ErrorCode errorCode, Member member) {
        return new MemberSanctionException(
                errorCode, member.getId(), MemberSanctionNotice.of(member, supportContactEmail));
    }
}
