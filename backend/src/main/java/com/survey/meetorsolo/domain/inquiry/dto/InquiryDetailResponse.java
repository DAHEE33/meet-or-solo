package com.survey.meetorsolo.domain.inquiry.dto;

import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** 문의 스레드 상세. 발화는 {@code id} 오름차순이다. */
public record InquiryDetailResponse(
        long inquiryId,
        InquiryCategory category,
        String title,
        InquiryStatus status,
        List<InquiryMessageResponse> messages,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt
) {
}
