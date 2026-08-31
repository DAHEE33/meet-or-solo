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
    REPORT_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "REPORT_INVALID_REQUEST", "신고 요청이 올바르지 않습니다."),
    REPORT_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_RESOURCE_NOT_FOUND", "신고할 매칭 정보를 찾을 수 없습니다."),
    REPORT_WINDOW_EXPIRED(HttpStatus.CONFLICT, "REPORT_WINDOW_EXPIRED", "신고 가능한 기간이 지났습니다."),
    REPORT_CONFLICT(HttpStatus.CONFLICT, "REPORT_CONFLICT", "현재 상태에서는 신고를 접수할 수 없습니다."),
    ADMIN_REPORT_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "ADMIN_REPORT_INVALID_REQUEST", "관리자 신고 요청 값이 올바르지 않습니다."),
    ADMIN_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_REPORT_NOT_FOUND", "신고 정보를 찾을 수 없습니다."),
    ADMIN_REPORT_STATUS_CONFLICT(HttpStatus.CONFLICT, "ADMIN_REPORT_STATUS_CONFLICT", "현재 신고 상태에서는 요청한 변경을 처리할 수 없습니다."),
    ADMIN_MEMBER_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "ADMIN_MEMBER_INVALID_REQUEST", "관리자 회원 요청 값이 올바르지 않습니다."),
    ADMIN_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_MEMBER_NOT_FOUND", "회원 정보를 찾을 수 없습니다."),
    ADMIN_MEMBER_STATUS_CONFLICT(HttpStatus.CONFLICT, "ADMIN_MEMBER_STATUS_CONFLICT", "현재 회원 상태에서는 요청한 제재를 처리할 수 없습니다."),
    ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT(HttpStatus.CONFLICT, "ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT", "활성 매칭이 있는 회원은 현재 제재할 수 없습니다."),
    ADMIN_ACTION_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "ADMIN_ACTION_IDEMPOTENCY_CONFLICT", "같은 Idempotency-Key가 다른 요청에 사용되었습니다."),
    MEMBER_SUSPENDED(HttpStatus.FORBIDDEN, "MEMBER_SUSPENDED", "이용이 일시 정지된 계정입니다."),
    MEMBER_BANNED(HttpStatus.FORBIDDEN, "MEMBER_BANNED", "이용이 영구 제한된 계정입니다."),
    MEMBER_INACTIVE(HttpStatus.FORBIDDEN, "MEMBER_INACTIVE", "현재 이용할 수 없는 계정입니다."),
    BLOCK_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "BLOCK_INVALID_REQUEST", "차단 요청이 올바르지 않습니다."),
    BLOCK_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOCK_RESOURCE_NOT_FOUND", "차단할 매칭 정보를 찾을 수 없습니다."),
    BLOCK_WINDOW_EXPIRED(HttpStatus.CONFLICT, "BLOCK_WINDOW_EXPIRED", "차단 가능한 기간이 지났습니다."),
    BLOCK_CONFLICT(HttpStatus.CONFLICT, "BLOCK_CONFLICT", "현재 상태에서는 차단할 수 없습니다."),
    INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_IMAGE", "JPEG, PNG, WEBP 이미지 파일만 업로드할 수 있습니다."),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "PROFILE_IMAGE_TOO_LARGE", "프로필 이미지 파일 크기 제한을 초과했습니다."),
    PROFILE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_IMAGE_NOT_FOUND", "등록된 프로필 이미지가 없습니다."),
    EMBEDDING_API_FAILED(HttpStatus.BAD_GATEWAY, "EMBEDDING_API_FAILED", "임베딩 생성에 실패했습니다."),
    AI_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "AI_CONSENT_REQUIRED", "AI 데이터 처리 동의가 필요합니다."),
    SIGNUP_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "SIGNUP_CONSENT_REQUIRED", "이용약관과 개인정보처리방침 동의가 필요합니다."),
    OBJECT_STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "OBJECT_STORAGE_ERROR", "이미지 저장소 처리에 실패했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    CHECKIN_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "CHECKIN_OUT_OF_RANGE", "체크인 가능 범위를 벗어났습니다."),
    LOW_LOCATION_ACCURACY(HttpStatus.BAD_REQUEST, "LOW_LOCATION_ACCURACY", "위치 정확도가 낮습니다. 다시 시도해 주세요."),
    FESTIVAL_LOCATION_UNAVAILABLE(HttpStatus.BAD_REQUEST, "FESTIVAL_LOCATION_UNAVAILABLE", "좌표 정보가 없는 축제는 체크인할 수 없습니다."),
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
