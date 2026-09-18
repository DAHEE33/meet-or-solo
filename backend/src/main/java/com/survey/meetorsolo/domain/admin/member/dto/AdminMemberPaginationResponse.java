package com.survey.meetorsolo.domain.admin.member.dto;

public record AdminMemberPaginationResponse(int size, boolean hasNext, String nextCursor) {
}
