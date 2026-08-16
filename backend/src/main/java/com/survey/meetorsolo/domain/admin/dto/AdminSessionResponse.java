package com.survey.meetorsolo.domain.admin.dto;

import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService.AdminMember;

public record AdminSessionResponse(long memberId, String nickname, String role) {

    public static AdminSessionResponse from(AdminMember member) {
        return new AdminSessionResponse(member.memberId(), member.nickname(), member.role());
    }
}
