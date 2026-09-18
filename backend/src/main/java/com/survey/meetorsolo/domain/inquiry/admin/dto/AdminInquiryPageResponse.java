package com.survey.meetorsolo.domain.inquiry.admin.dto;

import java.util.List;

/**
 * 관리자 문의 목록 응답.
 *
 * <p>{@code openCount}는 미처리({@code RECEIVED}·{@code IN_PROGRESS}) 건수다. 관리자 메뉴 badge가
 * 목록 1건만 요청해 이 값을 읽는다 — {@code AdminSafetyAlertPageResponse.openCount}와 같은
 * 방식이다.
 */
public record AdminInquiryPageResponse(
        List<AdminInquiryListItemResponse> items,
        long openCount,
        AdminInquiryPaginationResponse pagination
) {
}
