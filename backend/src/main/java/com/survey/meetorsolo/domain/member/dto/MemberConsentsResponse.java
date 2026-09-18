package com.survey.meetorsolo.domain.member.dto;

import java.util.List;

/** 동의 상태 목록. 조회 대상 유형은 항상 모두 채워서 반환한다. */
public record MemberConsentsResponse(List<MemberConsentResponse> consents) {
}
