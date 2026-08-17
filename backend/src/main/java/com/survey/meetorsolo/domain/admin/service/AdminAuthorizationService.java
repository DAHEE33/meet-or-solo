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
        accessPolicy.requireAccessible(member);
        if (!Member.ROLE_ADMIN.equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new AdminMember(member.getId(), member.getNickname(), member.getRole());
    }

    public record AdminMember(long memberId, String nickname, String role) {
    }
}
