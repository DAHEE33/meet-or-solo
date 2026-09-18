package com.survey.meetorsolo.domain.inquiry.admin.dto;

import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryPriority;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import java.time.OffsetDateTime;

/** 관리자 문의 목록 항목. 본문은 담지 않는다 — 목록에서 전체 본문을 실을 이유가 없다. */
public record AdminInquiryListItemResponse(
        long inquiryId,
        InquiryCategory category,
        String title,
        InquiryStatus status,
        InquiryPriority priority,
        AdminInquiryMemberSummaryResponse member,
        int messageCount,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt
) {
}
