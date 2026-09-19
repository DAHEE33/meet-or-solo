package com.survey.meetorsolo.domain.admin.dashboard.dto;

import java.time.OffsetDateTime;

/**
 * 대시보드에 띄우는 최근 문의 한 건.
 *
 * <p>문의 본문({@code inquiry_messages.body})은 담지 않는다. 제목만으로 무슨 문의인지
 * 구분되고, 스레드는 {@code /admin/inquiries}가 이미 보여준다.
 */
public record AdminDashboardRecentInquiryResponse(
        long inquiryId,
        String category,
        String title,
        String status,
        OffsetDateTime createdAt
) {
}
