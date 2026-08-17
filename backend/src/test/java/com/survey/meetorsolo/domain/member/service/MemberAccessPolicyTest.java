package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.member.entity.Member;
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
    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberAccessPolicy policy = new MemberAccessPolicy(
            members, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

    @Test
    void 만료되지_않은_정지는_접근을_거절한다() {
        Member member = Member.createNaverMember("suspended", "member", null);
        member.suspend(NOW.minusHours(1), NOW.plusHours(1));
        when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> policy.requireAccessible(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_SUSPENDED));
    }

    @Test
    void 만료된_정지는_lazy하게_제재전_상태로_복구한다() {
        Member member = Member.createNaverMember("expired", "member", null);
        member.suspend(NOW.minusDays(2), NOW.minusDays(1));
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(member));

        assertThat(policy.requireAccessible(2L)).isSameAs(member);
        assertThat(member.getStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
        assertThat(member.getSuspendedAt()).isNull();
        assertThat(member.getSuspendedUntil()).isNull();
    }

    @Test
    void 영구차단은_접근을_거절한다() {
        Member member = Member.createNaverMember("banned", "member", null);
        member.ban();

        assertThatThrownBy(() -> policy.requireAccessible(member))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_BANNED));
    }
}
