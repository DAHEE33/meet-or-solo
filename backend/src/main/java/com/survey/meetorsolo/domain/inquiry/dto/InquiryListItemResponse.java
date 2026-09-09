package com.survey.meetorsolo.domain.inquiry.dto;

import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import java.time.OffsetDateTime;

/**
 * 내 문의 목록 항목.
 *
 * <p>{@code hasUnreadAnswer}는 저장된 컬럼이 아니라 {@code Inquiry.hasUnreadAnswer()} 계산값이다.
 * 관리자 답변을 사용자에게 밀어줄 수단이 없어 이 값이 유일한 도달 신호다(docs/28 2.2).
 *
 * <p>{@code priority}는 담지 않는다. 관리자 내부 분류이고 사용자에게 알릴 값이 아니다.
 */
public record InquiryListItemResponse(
        long inquiryId,
        InquiryCategory category,
        String title,
        InquiryStatus status,
        boolean hasUnreadAnswer,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt
) {
}
