package com.survey.meetorsolo.global.error;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fields
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fields) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fields);
    }

    public record FieldError(
            String field,
            String message
    ) {
    }
}
