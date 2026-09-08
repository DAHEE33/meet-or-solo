package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberSanctionReason;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemberAccessPolicyTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-16T03:00:00Z");
    private static final String CONTACT_EMAIL = "support@example.test";
    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberAccessPolicy policy = new MemberAccessPolicy(
            members, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC), CONTACT_EMAIL);

    @Test
    void 만료되지_않은_정지는_접근을_거절한다() {
        Member member = Member.createNaverMember("suspended", "member", null);
        member.suspend(NOW.minusHours(1), NOW.plusHours(1), MemberSanctionReason.HARASSMENT.name());
        when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> policy.requireAccessible(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_SUSPENDED));
    }

    @Test
    void 정지_거절에는_사유와_종료시각_안내가_담긴다() {
        Member member = Member.createNaverMember("suspended", "member", null);
        member.suspend(NOW.minusHours(1), NOW.plusHours(1), MemberSanctionReason.NO_SHOW_ABUSE.name());
        when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> policy.requireAccessible(1L))
                .isInstanceOfSatisfying(MemberSanctionException.class, exception -> {
                    MemberSanctionNotice notice = exception.getNotice();
                    assertThat(notice.status()).isEqualTo(Member.STATUS_SUSPENDED);
                    assertThat(notice.suspendedUntil()).isEqualTo(NOW.plusHours(1));
                    assertThat(notice.reasonCode()).isEqualTo("NO_SHOW_ABUSE");
                    assertThat(notice.reasonMessage())
                            .isEqualTo(MemberSanctionReason.NO_SHOW_ABUSE.getUserMessage());
                    assertThat(notice.contactEmail()).isEqualTo(CONTACT_EMAIL);
                });
    }

    @Test
    void 만료된_정지는_lazy하게_제재전_상태로_복구한다() {
        Member member = Member.createNaverMember("expired", "member", null);
        member.suspend(NOW.minusDays(2), NOW.minusDays(1), MemberSanctionReason.OTHER.name());
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));

        assertThat(policy.requireAccessible(2L)).isSameAs(member);
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
        assertThat(member.getSuspendedAt()).isNull();
        assertThat(member.getSuspendedUntil()).isNull();
        assertThat(member.getSanctionReasonCode()).isNull();
    }

    @Test
    void 영구차단은_접근을_거절한다() {
        Member member = Member.createNaverMember("banned", "member", null);
        member.ban(MemberSanctionReason.SAFETY_RISK.name());

        assertThatThrownBy(() -> policy.requireAccessible(member))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_BANNED));
    }

    @Test
    void 영구차단_안내는_기간을_담지_않는다() {
        Member member = Member.createNaverMember("banned", "member", null);
        member.ban(MemberSanctionReason.FRAUD_OR_SCAM.name());

        assertThatThrownBy(() -> policy.requireAccessible(member))
                .isInstanceOfSatisfying(MemberSanctionException.class, exception -> {
                    MemberSanctionNotice notice = exception.getNotice();
                    assertThat(notice.status()).isEqualTo(Member.STATUS_BANNED);
                    assertThat(notice.suspendedUntil()).isNull();
                    assertThat(notice.reasonCode()).isEqualTo("FRAUD_OR_SCAM");
                });
    }

    @Test
    void 고객센터_이메일이_설정되지_않으면_안내에서_생략한다() {
        MemberAccessPolicy noContact = new MemberAccessPolicy(
                members, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC), "");
        Member member = Member.createNaverMember("banned", "member", null);
        member.ban(MemberSanctionReason.OTHER.name());

        assertThatThrownBy(() -> noContact.requireAccessible(member))
                .isInstanceOfSatisfying(MemberSanctionException.class,
                        exception -> assertThat(exception.getNotice().contactEmail()).isNull());
    }

    @Test
    void 정지_회원은_조회를_할_수_있다() {
        Member member = Member.createNaverMember("suspended", "member", null);
        member.suspend(NOW.minusHours(1), NOW.plusHours(1), MemberSanctionReason.HARASSMENT.name());
        when(members.findByIdForUpdate(6L)).thenReturn(Optional.of(member));

        // 정지는 조회를 막지 않는다. 전면 차단하면 자기 제재 기간조차 확인할 수 없다.
        assertThat(policy.requireBrowsable(6L)).isSameAs(member);
    }

    @Test
    void 정지_회원은_로그인을_할_수_있다() {
        Member member = Member.createNaverMember("suspended", "member", null);
        member.suspend(NOW.minusHours(1), NOW.plusHours(1), MemberSanctionReason.NO_SHOW_ABUSE.name());

        policy.requireSignedIn(member);
    }

    @Test
    void 영구차단_회원은_조회도_로그인도_할_수_없다() {
        Member member = Member.createNaverMember("banned", "member", null);
        member.ban(MemberSanctionReason.SAFETY_RISK.name());
        when(members.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> policy.requireBrowsable(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_BANNED));
        assertThatThrownBy(() -> policy.requireSignedIn(member))
                .isInstanceOf(MemberSanctionException.class);
    }

    @Test
    void 제재_상태가_아니면_안내를_주지_않는다() {
        Member member = Member.createNaverMember("active", "member", null);
        when(members.findByIdForUpdate(3L)).thenReturn(Optional.of(member));

        assertThat(policy.findSanctionNotice(3L)).isNull();
    }

    @Test
    void 정지가_만료된_회원은_안내를_주지_않는다() {
        Member member = Member.createNaverMember("expired", "member", null);
        member.suspend(NOW.minusDays(2), NOW.minusDays(1), MemberSanctionReason.HARASSMENT.name());
        when(members.findByIdForUpdate(4L)).thenReturn(Optional.of(member));

        assertThat(policy.findSanctionNotice(4L)).isNull();
    }

    @Test
    void 제재_중인_회원은_안내를_조회할_수_있다() {
        Member member = Member.createNaverMember("suspended", "member", null);
        member.suspend(NOW.minusHours(1), NOW.plusHours(2), MemberSanctionReason.COMMUNITY_GUIDELINE.name());
        when(members.findByIdForUpdate(5L)).thenReturn(Optional.of(member));

        MemberSanctionNotice notice = policy.findSanctionNotice(5L);

        assertThat(notice).isNotNull();
        assertThat(notice.reasonCode()).isEqualTo("COMMUNITY_GUIDELINE");
        assertThat(notice.suspendedUntil()).isEqualTo(NOW.plusHours(2));
    }
}
