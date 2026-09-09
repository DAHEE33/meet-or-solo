package com.survey.meetorsolo.domain.inquiry.admin.dto;

/**
 * 관리자가 직접 지정할 수 있는 문의 상태.
 *
 * <p>{@code RECEIVED}는 접수 시 자동으로 붙는 초기 상태이므로 되돌릴 수 없고,
 * {@code ANSWERED}는 답변 등록으로만 만들어진다. 답변 없이 상태만 답변 완료로 바꾸면
 * 사용자에게 답변이 온 것처럼 badge가 켜진다.
 *
 * <p>{@code AdminReportTargetStatus}가 {@code SUBMITTED}를 제외한 것과 같은 이유다.
 */
public enum AdminInquiryTargetStatus {

    IN_PROGRESS,
    CLOSED
}
