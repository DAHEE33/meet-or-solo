package com.survey.meetorsolo.domain.admin.service;

import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthorizationService {

    private final MemberRepository members;
    private final MemberAccessPolicy accessPolicy;

    public AdminAuthorizationService(MemberRepository members, MemberAccessPolicy accessPolicy) {
        this.members = members;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public AdminMember requireAdmin(long memberId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        // 역할과 계정 종류를 먼저 본다. 관리자가 아니면 제재와 무관하게 FORBIDDEN이다.
        //
        // 순서를 바꾸면 정지된 일반 회원이 MEMBER_SUSPENDED를 받는다. 관리자 여부 확인은
        // 활동이 아니라 조회이고, 제재 사유를 알릴 자리도 아니다.
        if (!isLocalAdmin(member)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        accessPolicy.requireAccessible(member);
        return new AdminMember(member.getId(), member.getNickname(), member.getRole());
    }

    /**
     * 관리자 진입은 {@code /admin/login}의 로컬 계정 하나로만 연다.
     *
     * <p>역할만 보면 소셜 계정에 {@code role='ADMIN'}을 붙이는 것으로도 관리자 화면이 열린다.
     * 그 경로(마이페이지 → 관리자 기능)를 걷어냈으므로 서버도 같이 닫는다. 화면에서 링크만
     * 지우면 URL을 아는 사람은 그대로 들어온다.
     *
     * <p>소셜 계정에 role이 잘못 올라가 있어도 FORBIDDEN이다. 사유를 갈라 알리면 어느 계정이
     * 관리자 역할을 갖고 있는지가 응답으로 드러난다.
     */
    private static boolean isLocalAdmin(Member member) {
        return Member.ROLE_ADMIN.equals(member.getRole())
                && Member.PROVIDER_LOCAL.equals(member.getProvider());
    }

    public record AdminMember(long memberId, String nickname, String role) {
    }
}
