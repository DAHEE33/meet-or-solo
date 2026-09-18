package com.survey.meetorsolo.domain.inquiry.admin.dto;

/** {@code AdminReportPaginationResponse}와 같은 형태의 cursor 페이징 메타. */
public record AdminInquiryPaginationResponse(
        int size,
        boolean hasNext,
        String nextCursor
) {
}
