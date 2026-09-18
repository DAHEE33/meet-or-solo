package com.survey.meetorsolo.domain.inquiry.dto;

import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 문의 등록 요청.
 *
 * <p><b>{@code priority}를 받지 않는다.</b> 사용자가 긴급을 고르게 하면 사실상 전부 긴급으로
 * 들어와 우선순위가 무의미해진다. 긴급 지정은 관리자 {@code PATCH}만 한다
 * (docs/29_MEMBER_INQUIRY_DESIGN.md 확정 5번).
 */
public record InquiryCreateRequest(
        @NotNull InquiryCategory category,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 2000) String body
) {
}
