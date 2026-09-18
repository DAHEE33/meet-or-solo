package com.survey.meetorsolo.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record AgreeConsentRequest(
        @NotBlank(message = "동의 유형은 필수입니다.")
        String consentType
) {
}
