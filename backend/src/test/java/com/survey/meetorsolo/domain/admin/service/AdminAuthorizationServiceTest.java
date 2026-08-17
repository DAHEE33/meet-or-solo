package com.survey.meetorsolo.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminAuthorizationServiceTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberAccessPolicy accessPolicy = mock(MemberAccessPolicy.class);
    private final AdminAuthorizationService service = new AdminAuthorizationService(members, accessPolicy);

    @Test
    void 존재하지_않는_인증_회원은_401이다() {
        when(members.findById(1L)).thenReturn(Optional.empty());
        assertError(1L, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 일반_회원은_403이다() {
        Member member = member(1L, "회원", Member.ROLE_USER);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        assertError(1L, ErrorCode.FORBIDDEN);
    }

    @Test
    void DB의_ADMIN_회원_최소_snapshot을_반환한다() {
        Member member = member(2L, "관리자", Member.ROLE_ADMIN);
        when(members.findById(2L)).thenReturn(Optional.of(member));
        assertThat(service.requireAdmin(2L))
                .isEqualTo(new AdminAuthorizationService.AdminMember(2L, "관리자", "ADMIN"));
    }

    private Member member(long id, String nickname, String role) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getNickname()).thenReturn(nickname);
        when(member.getRole()).thenReturn(role);
        return member;
    }

    private void assertError(long id, ErrorCode code) {
        assertThatThrownBy(() -> service.requireAdmin(id))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
