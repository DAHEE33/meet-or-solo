package com.survey.meetorsolo.domain.inquiry.admin.service;

import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryPriority;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * 관리자 문의 목록 filter. {@code AdminReportFilter}와 같은 형태다.
 *
 * <p>{@link #fingerprint()}가 cursor payload에 함께 들어간다. filter를 바꾸고 이전 cursor를
 * 그대로 이어 읽으면 페이지 경계에서 항목이 중복·누락되므로, codec이 fingerprint 불일치를
 * 거절한다.
 */
public record AdminInquiryFilter(
        InquiryStatus status,
        InquiryCategory category,
        InquiryPriority priority,
        OffsetDateTime createdFrom,
        OffsetDateTime createdTo
) {
    public String fingerprint() {
        String canonical = value(status) + "|" + value(category) + "|" + value(priority) + "|"
                + value(createdFrom) + "|" + value(createdTo);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }
}
