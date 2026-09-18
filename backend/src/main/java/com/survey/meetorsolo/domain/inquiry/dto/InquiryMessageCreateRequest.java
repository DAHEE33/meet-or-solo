package com.survey.meetorsolo.domain.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 스레드에 발화를 덧붙이는 요청. 사용자 추가 질문과 관리자 답변이 같은 형태를 쓴다. */
public record InquiryMessageCreateRequest(
        @NotBlank @Size(max = 2000) String body
) {
}
