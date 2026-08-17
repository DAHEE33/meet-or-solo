package com.survey.meetorsolo.domain.admin.member.dto;

import java.util.List;

public record AdminMemberPageResponse(
        List<AdminMemberListItemResponse> items,
        AdminMemberPaginationResponse pagination
) {
}
