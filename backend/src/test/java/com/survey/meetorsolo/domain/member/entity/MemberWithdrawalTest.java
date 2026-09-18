package com.survey.meetorsolo.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * 탈퇴와 재가입의 상태 전이({@code docs/19} 4.4).
 *
 * <p>제재 세탁 방지가 이 항목의 핵심이라 잔여 정지 기간 이어받기를 여기서 못 박는다.
 */
class MemberWithdrawalTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-09T12:00:00+09:00");

    @Test
    void 탈퇴는_개인정보를_익명화하고_닉네임까지_지운다() {
        Member member = active();
        member.completeProfile("원래닉네임", "user@example.test", "소개글",
                new byte[]{1, 2}, new byte[]{3, 4});

        member.withdraw(NOW, false, false);

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_WITHDRAWN);
        // 표시 문구를 컬럼에 넣지 않는다. '탈퇴한 회원'은 조회 SQL이 status로 만든다.
        assertThat(member.getNickname()).isNull();
        assertThat(member.getEmail()).isNull();
        assertThat(member.getIntro()).isNull();
        assertThat(member.getProfileImageUrl()).isNull();
        assertThat(member.getProfileImageObjectKey()).isNull();
        assertThat(member.getGenderEncrypted()).isNull();
        assertThat(member.getAgeRangeEncrypted()).isNull();
        assertThat(member.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(member.getWithdrawnFromStatus()).isEqualTo(Member.STATUS_ACTIVE);
        assertThat(member.isWithdrawnByAdmin()).isFalse();
        assertThat(member.isRejoinBlocked()).isFalse();
    }

    /**
     * {@code V19}의 {@code chk_members_suspension_period}와 {@code V27}의
     * {@code chk_members_sanction_reason_presence}는 제재 상태가 아닌 회원에게 그 값이 남는
     * 것을 금지한다. 탈퇴가 제재 컬럼을 비우지 않으면 저장 자체가 실패한다.
     */
    @Test
    void 정지_중_탈퇴는_제재컬럼을_비우고_잔여기간을_스냅샷으로_옮긴다() {
        Member member = active();
        OffsetDateTime until = NOW.plusDays(30);
        member.suspend(NOW.minusDays(1), until, "HARASSMENT");

        member.withdraw(NOW, false, false);

        assertThat(member.getSuspendedAt()).isNull();
        assertThat(member.getSuspendedUntil()).isNull();
        assertThat(member.getSanctionReasonCode()).isNull();
        assertThat(member.getStatusBeforeSanction()).isNull();

        assertThat(member.getWithdrawnFromStatus()).isEqualTo(Member.STATUS_SUSPENDED);
        assertThat(member.getWithdrawnSuspendedUntil()).isEqualTo(until);
        assertThat(member.getWithdrawnSanctionReasonCode()).isEqualTo("HARASSMENT");
    }

    /** 30일 정지 → 탈퇴 → 재가입으로 23일이 세탁되면 안 된다. */
    @Test
    void 재가입은_잔여_정지기간을_이어받는다() {
        Member member = active();
        OffsetDateTime until = NOW.plusDays(30);
        member.suspend(NOW.minusDays(1), until, "HARASSMENT");
        member.withdraw(NOW, false, false);

        member.rejoin(NOW.plusDays(7));

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_SUSPENDED);
        assertThat(member.getSuspendedUntil()).isEqualTo(until);
        assertThat(member.getSanctionReasonCode()).isEqualTo("HARASSMENT");
        // 프로필이 익명화되어 비어 있으므로 정지 해제 후 돌아갈 상태는 PROFILE_REQUIRED다.
        assertThat(member.getStatusBeforeSanction()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
        assertThat(member.getWithdrawnAt()).isNull();
        assertThat(member.getWithdrawnSuspendedUntil()).isNull();
    }

    @Test
    void 정지기간이_이미_지났으면_재가입은_프로필_재입력_상태다() {
        Member member = active();
        member.suspend(NOW.minusDays(1), NOW.plusDays(1), "HARASSMENT");
        member.withdraw(NOW, false, false);

        member.rejoin(NOW.plusDays(7));

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
        assertThat(member.getSuspendedUntil()).isNull();
        assertThat(member.getSanctionReasonCode()).isNull();
        assertThat(member.getStatusBeforeSanction()).isNull();
    }

    @Test
    void 재가입은_탈퇴_스냅샷을_모두_비운다() {
        Member member = active();
        member.withdraw(NOW, false, false);

        member.rejoin(NOW.plusDays(7));

        assertThat(member.getWithdrawnAt()).isNull();
        assertThat(member.getWithdrawnFromStatus()).isNull();
        assertThat(member.isWithdrawnByAdmin()).isFalse();
        assertThat(member.isRejoinBlocked()).isFalse();
        assertThat(member.getWithdrawnSuspendedUntil()).isNull();
        assertThat(member.getWithdrawnSanctionReasonCode()).isNull();
    }

    /**
     * 영구차단 회원은 로그인과 {@code /api/**} 요청이 모두 막혀 본인 탈퇴 경로에 닿을 수 없다.
     * 닿았다면 접근 판정이 뚫린 것이므로 엔티티에서도 막는다.
     */
    @Test
    void 영구차단_회원은_본인_탈퇴_경로를_쓸_수_없다() {
        Member member = active();
        member.ban("FRAUD_OR_SCAM");

        assertThatThrownBy(() -> member.withdraw(NOW, false, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_BANNED);
    }

    /** 영구차단 회원의 개인정보 삭제 요청은 관리자 강제 탈퇴로만 처리된다. */
    @Test
    void 관리자는_영구차단_회원을_강제_탈퇴시킬_수_있다() {
        Member member = active();
        member.ban("FRAUD_OR_SCAM");

        member.withdraw(NOW, true, true);

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_WITHDRAWN);
        assertThat(member.getWithdrawnFromStatus()).isEqualTo(Member.STATUS_BANNED);
        assertThat(member.isWithdrawnByAdmin()).isTrue();
        assertThat(member.isRejoinBlocked()).isTrue();
        assertThat(member.getEmail()).isNull();
    }

    @Test
    void 본인_탈퇴는_재가입을_차단할_수_없다() {
        Member member = active();

        assertThatThrownBy(() -> member.withdraw(NOW, false, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_ACTIVE);
    }

    @Test
    void 이미_탈퇴한_회원은_다시_탈퇴할_수_없다() {
        Member member = active();
        member.withdraw(NOW, false, false);

        assertThatThrownBy(() -> member.withdraw(NOW, false, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 탈퇴_상태가_아니면_재가입할_수_없다() {
        Member member = active();

        assertThatThrownBy(() -> member.rejoin(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Member active() {
        Member member = Member.createKakaoMember(
                "withdrawal-test", "user@example.test", "원래닉네임", "https://image.example.test/a.png");
        member.completeProfile("원래닉네임", "user@example.test", "소개글",
                new byte[]{1, 2}, new byte[]{3, 4});
        return member;
    }
}
