package com.survey.meetorsolo.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    /**
     * 마이페이지가 관리자 메뉴 노출 여부를 판단하려고 이 endpoint를 조회한다. 정지된 일반
     * 회원에게 제재 예외를 던지면 마이페이지를 열 때마다 제재 안내 팝업이 뜬다.
     */
    @Test
    void 정지된_일반_회원도_제재가_아니라_403이다() {
        Member member = member(1L, "정지회원", Member.ROLE_USER);
        when(members.findById(1L)).thenReturn(Optional.of(member));

        assertError(1L, ErrorCode.FORBIDDEN);
        // 역할 확인에서 끝나므로 제재 판정까지 가지 않는다.
        verify(accessPolicy, never()).requireAccessible(member);
    }

    @Test
    void 관리자_회원은_제재_판정을_거친다() {
        Member member = member(2L, "관리자", Member.ROLE_ADMIN);
        when(members.findById(2L)).thenReturn(Optional.of(member));

        service.requireAdmin(2L);

        // 정지된 관리자는 관리자 기능을 쓸 수 없어야 한다.
        verify(accessPolicy).requireAccessible(member);
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
