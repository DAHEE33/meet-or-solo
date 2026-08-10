package com.survey.meetorsolo.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "요청 값이 올바르지 않습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "요청 값 검증에 실패했습니다."),
    OAUTH_LOGIN_FAILED(HttpStatus.BAD_REQUEST, "OAUTH_LOGIN_FAILED", "OAuth 로그인 처리에 실패했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    MATCHING_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "MATCHING_INVALID_REQUEST", "매칭 요청이 올바르지 않습니다."),
    MATCHING_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCHING_RESOURCE_NOT_FOUND", "매칭 리소스를 찾을 수 없습니다."),
    MATCHING_CONFLICT(HttpStatus.CONFLICT, "MATCHING_CONFLICT", "현재 상태에서는 매칭 요청을 처리할 수 없습니다."),
    MATCHING_COMPLETION_LOCKED(
            HttpStatus.CONFLICT,
            "MATCHING_COMPLETION_LOCKED",
            "현재 매칭 유효시간이 끝난 뒤 다시 신청할 수 있습니다."
    ),
    MATCHING_MEETING_POINT_NOT_READY(
            HttpStatus.CONFLICT,
            "MATCHING_MEETING_POINT_NOT_READY",
            "선택한 축제의 만남 장소를 준비하고 있습니다."
    ),
    MATCHING_ARRIVAL_DEADLINE_EXCEEDED(
            HttpStatus.CONFLICT,
            "MATCHING_ARRIVAL_DEADLINE_EXCEEDED",
            "도착 예정 시간을 최종 마감 안으로 선택해주세요."
    ),
    INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_IMAGE", "JPEG, PNG, WEBP 이미지 파일만 업로드할 수 있습니다."),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "PROFILE_IMAGE_TOO_LARGE", "프로필 이미지 파일 크기 제한을 초과했습니다."),
    PROFILE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_IMAGE_NOT_FOUND", "등록된 프로필 이미지가 없습니다."),
    OBJECT_STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "OBJECT_STORAGE_ERROR", "이미지 저장소 처리에 실패했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
