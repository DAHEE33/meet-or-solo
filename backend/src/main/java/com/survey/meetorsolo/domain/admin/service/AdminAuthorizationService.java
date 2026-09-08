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
        // 역할을 먼저 본다. 관리자가 아니면 제재와 무관하게 FORBIDDEN이다.
        //
        // 순서를 바꾸면 정지된 일반 회원이 MEMBER_SUSPENDED를 받는다. 마이페이지가 관리자
        // 메뉴 노출 여부를 판단하려고 이 endpoint를 조회하므로, 그때마다 제재 안내 팝업이
        // 뜬다. 관리자 여부 확인은 활동이 아니라 조회이고, 제재 사유를 알릴 자리도 아니다.
        if (!Member.ROLE_ADMIN.equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        accessPolicy.requireAccessible(member);
        return new AdminMember(member.getId(), member.getNickname(), member.getRole());
    }

    public record AdminMember(long memberId, String nickname, String role) {
    }
}
