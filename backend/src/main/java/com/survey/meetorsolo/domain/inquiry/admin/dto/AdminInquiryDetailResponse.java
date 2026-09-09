package com.survey.meetorsolo.domain.inquiry.admin.dto;

import com.survey.meetorsolo.domain.inquiry.dto.InquiryMessageResponse;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryPriority;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** 관리자 문의 상세. 발화는 {@code id} 오름차순이다. */
public record AdminInquiryDetailResponse(
        long inquiryId,
        InquiryCategory category,
        String title,
        InquiryStatus status,
        InquiryPriority priority,
        AdminInquiryMemberSummaryResponse member,
        List<InquiryMessageResponse> messages,
        OffsetDateTime lastMessageAt,
        OffsetDateTime lastAnsweredAt,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt
) {
}
