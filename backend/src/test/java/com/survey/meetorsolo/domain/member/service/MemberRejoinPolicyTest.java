package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.global.error.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * 탈퇴 후 재가입 쿨오프 판정({@code docs/19} 4.4).
 *
 * <p>7일 경계와 영구 거부를 못 박는다. 쿨오프가 헐거워지면 탈퇴가 제재 회피 경로가 된다.
 */
class MemberRejoinPolicyTest {

    private static final OffsetDateTime WITHDRAWN_AT = OffsetDateTime.parse("2026-09-01T12:00:00+09:00");

    @Test
    void 탈퇴_회원이_아니면_아무것도_하지_않는다() {
        Member member = active();

        assertThat(policyAt(WITHDRAWN_AT).rejoinIfWithdrawn(member)).isFalse();
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_ACTIVE);
    }

    @Test
    void 탈퇴_6일_23시간_뒤에는_재가입이_거부된다() {
        Member member = withdrawn(false, false);
        OffsetDateTime now = WITHDRAWN_AT.plusDays(6).plusHours(23);

        assertThatThrownBy(() -> policyAt(now).rejoinIfWithdrawn(member))
                .isInstanceOfSatisfying(MemberSanctionException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_REJOIN_BLOCKED);
                    MemberSanctionNotice notice = exception.getNotice();
                    assertThat(notice.status()).isEqualTo(Member.STATUS_WITHDRAWN);
                    assertThat(notice.reasonCode())
                            .isEqualTo(MemberSanctionNotice.REASON_CODE_REJOIN_COOLDOWN);
                    assertThat(notice.rejoinAvailableAt()).isEqualTo(WITHDRAWN_AT.plusDays(7));
                });
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_WITHDRAWN);
    }

    @Test
    void 탈퇴_7일_1분_뒤에는_계정이_부활한다() {
        Member member = withdrawn(false, false);
        OffsetDateTime now = WITHDRAWN_AT.plusDays(7).plusMinutes(1);

        assertThat(policyAt(now).rejoinIfWithdrawn(member)).isTrue();
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
        assertThat(member.getWithdrawnAt()).isNull();
    }

    /** 경계는 "쿨오프 시각 이전이면 거부"다. 정확히 7일이 지난 시점은 허용한다. */
    @Test
    void 정확히_7일이_되는_시점은_허용한다() {
        Member member = withdrawn(false, false);

        assertThat(policyAt(WITHDRAWN_AT.plusDays(7)).rejoinIfWithdrawn(member)).isTrue();
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
    }

    /**
     * 관리자 강제 탈퇴는 7일 규칙의 예외다. 쿨오프로 풀리면 관리자가 내린 조치가
     * "일주일 기다리면 되는 것"이 된다.
     */
    @Test
    void 재가입_차단된_강제_탈퇴는_7일이_지나도_영구_거부된다() {
        Member member = withdrawn(true, true);
        OffsetDateTime now = WITHDRAWN_AT.plusYears(3);

        assertThatThrownBy(() -> policyAt(now).rejoinIfWithdrawn(member))
                .isInstanceOfSatisfying(MemberSanctionException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_REJOIN_BLOCKED);
                    assertThat(exception.getNotice().reasonCode())
                            .isEqualTo(MemberSanctionNotice.REASON_CODE_REJOIN_BLOCKED);
                    // 영구 거부에는 재가입 가능 시각이 없다. 값이 실리면 화면이 대기 안내를 띄운다.
                    assertThat(exception.getNotice().rejoinAvailableAt()).isNull();
                });
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_WITHDRAWN);
    }

    /** 로그인이 막힌 회원의 탈퇴 대행은 제재가 아니므로 쿨오프만 적용한다. */
    @Test
    void 재가입_차단하지_않은_강제_탈퇴는_7일_뒤_부활한다() {
        Member member = withdrawn(true, false);

        assertThat(policyAt(WITHDRAWN_AT.plusDays(7)).rejoinIfWithdrawn(member)).isTrue();
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
    }

    @Test
    void 안내에는_고객센터_이메일이_실린다() {
        Member member = withdrawn(false, false);

        assertThatThrownBy(() -> policyAt(WITHDRAWN_AT).rejoinIfWithdrawn(member))
                .isInstanceOfSatisfying(MemberSanctionException.class, exception ->
                        assertThat(exception.getNotice().contactEmail())
                                .isEqualTo("support@example.test"));
    }

    /**
     * <b>회귀 방지</b>: 로그인 화면은 302 redirect로 오기 때문에 예외에 실은 안내를 볼 수 없고,
     * {@code GET /api/auth/sanction-notice}로 다시 조회한다. 그 조회가 {@code null}을 주면
     * 화면이 제재용 포괄 문구로 떨어져 탈퇴한 사용자에게 "계정이 제재되어"라는 틀린 안내가 뜬다.
     * 실제로 그 버그가 있었다.
     */
    @Test
    void 조회_경로도_탈퇴_회원에게_안내를_준다() {
        Member member = withdrawn(false, false);

        MemberSanctionNotice notice = policyAt(WITHDRAWN_AT.plusDays(1)).noticeFor(member);

        assertThat(notice).isNotNull();
        assertThat(notice.status()).isEqualTo(Member.STATUS_WITHDRAWN);
        assertThat(notice.reasonCode()).isEqualTo(MemberSanctionNotice.REASON_CODE_REJOIN_COOLDOWN);
        assertThat(notice.rejoinAvailableAt()).isEqualTo(WITHDRAWN_AT.plusDays(7));
        // 문의 경로가 없으면 사용자가 물어볼 곳이 없다.
        assertThat(notice.contactEmail()).isEqualTo("support@example.test");
    }

    @Test
    void 조회_경로의_영구_거부_안내는_재가입_시각이_없다() {
        Member member = withdrawn(true, true);

        MemberSanctionNotice notice = policyAt(WITHDRAWN_AT.plusDays(1)).noticeFor(member);

        assertThat(notice.reasonCode()).isEqualTo(MemberSanctionNotice.REASON_CODE_REJOIN_BLOCKED);
        assertThat(notice.rejoinAvailableAt()).isNull();
        assertThat(notice.contactEmail()).isEqualTo("support@example.test");
    }

    /** 쿨오프가 지났어도 아직 탈퇴 상태면 안내를 준다. 부활은 로그인 시점에 일어난다. */
    @Test
    void 쿨오프가_지난_탈퇴_회원도_조회하면_안내가_있다() {
        Member member = withdrawn(false, false);

        MemberSanctionNotice notice = policyAt(WITHDRAWN_AT.plusDays(30)).noticeFor(member);

        assertThat(notice).isNotNull();
        assertThat(notice.rejoinAvailableAt()).isEqualTo(WITHDRAWN_AT.plusDays(7));
    }

    @Test
    void 탈퇴_회원이_아니면_조회에도_안내가_없다() {
        assertThat(policyAt(WITHDRAWN_AT).noticeFor(active())).isNull();
    }

    /** 안내 문구가 "제재"라고 단정하면 탈퇴한 사용자에게 틀린 말이 된다. */
    @Test
    void 탈퇴_안내는_제재라고_말하지_않는다() {
        Member member = withdrawn(false, false);

        MemberSanctionNotice notice = policyAt(WITHDRAWN_AT.plusDays(1)).noticeFor(member);

        assertThat(notice.reasonMessage()).contains("탈퇴");
        assertThat(notice.reasonMessage()).doesNotContain("제재");
        assertThat(notice.reasonMessage()).doesNotContain("정지");
    }

    /** 신고자 보호. 재가입 안내에도 신고 관련 문구가 들어가면 안 된다. */
    @Test
    void 재가입_안내는_신고_관련_문구를_담지_않는다() {
        for (OffsetDateTime availableAt : new OffsetDateTime[]{null, WITHDRAWN_AT.plusDays(7)}) {
            MemberSanctionNotice notice =
                    MemberSanctionNotice.forWithdrawn(availableAt, "support@example.test");
            assertThat(notice.reasonMessage())
                    .doesNotContain("신고").doesNotContain("누적").doesNotContain("제보");
        }
    }

    private static MemberRejoinPolicy policyAt(OffsetDateTime now) {
        return new MemberRejoinPolicy(
                Clock.fixed(now.toInstant(), ZoneId.of("Asia/Seoul")), "support@example.test");
    }

    private static Member active() {
        Member member = Member.createKakaoMember("rejoin-test", "user@example.test", "닉네임", null);
        member.completeProfile("닉네임", "user@example.test", null, new byte[]{1}, new byte[]{2});
        return member;
    }

    private static Member withdrawn(boolean byAdmin, boolean blockRejoin) {
        Member member = active();
        member.withdraw(WITHDRAWN_AT, byAdmin, blockRejoin);
        return member;
    }
}
