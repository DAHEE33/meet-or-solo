package com.survey.meetorsolo.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePreferenceEmbeddingRequest(
        @NotBlank(message = "선호도 텍스트는 필수입니다.")
        @Size(max = 2000, message = "선호도 텍스트는 2000자 이하여야 합니다.")
        String preferenceText
) {
}
