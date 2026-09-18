package com.survey.meetorsolo.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * 제재 사유 code의 lifecycle 검증.
 *
 * <p>제재가 풀렸는데 사유가 남아 있으면 DB의 {@code chk_members_sanction_reason_presence}가
 * insert/update를 거부한다. 해제 경로마다 사유를 지우는지 여기서 확인한다.
 */
class MemberSanctionReasonLifecycleTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-08T12:00:00+09:00");

    @Test
    void 정지는_사유_code를_함께_저장한다() {
        Member member = activeMember();

        member.suspend(NOW, NOW.plusDays(7), MemberSanctionReason.HARASSMENT.name());

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_SUSPENDED);
        assertThat(member.getSanctionReasonCode()).isEqualTo("HARASSMENT");
    }

    @Test
    void 영구차단은_사유_code를_함께_저장하고_기간을_비운다() {
        Member member = activeMember();

        member.ban(MemberSanctionReason.SAFETY_RISK.name());

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_BANNED);
        assertThat(member.getSanctionReasonCode()).isEqualTo("SAFETY_RISK");
        assertThat(member.getSuspendedUntil()).isNull();
    }

    @Test
    void 정지_해제는_사유_code를_지운다() {
        Member member = activeMember();
        member.suspend(NOW, NOW.plusDays(7), MemberSanctionReason.NO_SHOW_ABUSE.name());

        member.unsuspend();

        assertThat(member.getSanctionReasonCode()).isNull();
    }

    @Test
    void 영구차단_해제는_사유_code를_지운다() {
        Member member = activeMember();
        member.ban(MemberSanctionReason.FRAUD_OR_SCAM.name());

        member.unban();

        assertThat(member.getSanctionReasonCode()).isNull();
    }

    @Test
    void 정지_만료_복구는_사유_code를_지운다() {
        Member member = activeMember();
        member.suspend(NOW.minusDays(2), NOW.minusDays(1), MemberSanctionReason.OTHER.name());

        assertThat(member.restoreExpiredSuspension(NOW)).isTrue();
        assertThat(member.getSanctionReasonCode()).isNull();
    }

    @Test
    void 정지에서_영구차단으로_올릴_때_사유_code를_새_값으로_바꾼다() {
        Member member = activeMember();
        member.suspend(NOW, NOW.plusDays(3), MemberSanctionReason.COMMUNITY_GUIDELINE.name());

        member.ban(MemberSanctionReason.SAFETY_RISK.name());

        assertThat(member.getSanctionReasonCode()).isEqualTo("SAFETY_RISK");
    }

    @Test
    void 사유_code가_없으면_제재할_수_없다() {
        assertThatThrownBy(() -> activeMember().suspend(NOW, NOW.plusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activeMember().suspend(NOW, NOW.plusDays(1), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activeMember().ban(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 정지 회원의 프로필 수정이 status를 ACTIVE로 덮으면 제재가 조용히 풀리고,
     * suspended_until과 사유가 남아 DB CHECK 제약 위반으로 저장 자체가 실패한다.
     */
    @Test
    void 정지_중_프로필_수정은_제재를_풀지_않는다() {
        Member member = activeMember();
        member.suspend(NOW, NOW.plusDays(7), MemberSanctionReason.HARASSMENT.name());

        member.completeProfile("바뀐닉", "a@b.test", "소개", new byte[]{1}, new byte[]{2});

        assertThat(member.getNickname()).isEqualTo("바뀐닉");
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_SUSPENDED);
        assertThat(member.getSuspendedUntil()).isEqualTo(NOW.plusDays(7));
        assertThat(member.getSanctionReasonCode()).isEqualTo("HARASSMENT");
    }

    @Test
    void 프로필_미완성_회원의_프로필_입력은_ACTIVE로_승격한다() {
        Member member = activeMember();

        member.completeProfile("닉", null, null, new byte[]{1}, new byte[]{2});

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_ACTIVE);
    }

    @Test
    void 정지_중_프로필을_완성하면_해제_후_ACTIVE로_돌아간다() {
        // 그대로 두면 정지가 풀린 뒤 PROFILE_REQUIRED로 복구되어 다시 가입 화면으로 보내진다.
        Member member = activeMember();
        member.suspend(NOW, NOW.plusDays(7), MemberSanctionReason.OTHER.name());
        assertThat(member.getStatusBeforeSanction()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);

        member.completeProfile("닉", null, null, new byte[]{1}, new byte[]{2});
        member.unsuspend();

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_ACTIVE);
    }

    private static Member activeMember() {
        return Member.createNaverMember("provider-user", "member", null);
    }
}
