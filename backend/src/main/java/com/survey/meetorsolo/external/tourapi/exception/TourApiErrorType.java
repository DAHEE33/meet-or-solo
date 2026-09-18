package com.survey.meetorsolo.external.tourapi.exception;

public enum TourApiErrorType {
    CONFIGURATION("관광공사 API 설정이 올바르지 않습니다."),
    NETWORK("관광공사 API 네트워크 호출에 실패했습니다."),
    HTTP("관광공사 API HTTP 호출에 실패했습니다."),
    AUTHORIZATION("관광공사 API 인증 또는 권한 확인에 실패했습니다."),
    RATE_LIMIT("관광공사 API 호출 한도를 초과했습니다."),
    REMOTE("관광공사 API가 오류를 반환했습니다."),
    MALFORMED_RESPONSE("관광공사 API 응답 형식이 올바르지 않습니다.");

    private final String defaultMessage;

    TourApiErrorType(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
