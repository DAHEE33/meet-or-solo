package com.survey.meetorsolo.domain.inquiry.dto;

import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessageAuthorType;
import java.time.OffsetDateTime;

/**
 * 스레드 발화 1건.
 *
 * <p>답변 작성자의 {@code memberId}·닉네임을 담지 않는다. 화면은 {@code authorType}만 보고
 * "운영팀"으로 표시하며, 관리자 개인을 특정할 이유가 없다(docs/28 7절).
 */
public record InquiryMessageResponse(
        long messageId,
        InquiryMessageAuthorType authorType,
        String body,
        OffsetDateTime createdAt
) {
}
