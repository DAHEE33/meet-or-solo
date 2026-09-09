package com.survey.meetorsolo.domain.inquiry.dto;

import java.util.List;

/**
 * 내 문의 목록. offset 페이징이다 — 본인 문의는 건수가 적어 cursor가 필요 없고
 * {@code ContentCommentListResponse}와 같은 형태를 재사용한다(docs/28 5.2).
 */
public record InquiryListResponse(
        List<InquiryListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
