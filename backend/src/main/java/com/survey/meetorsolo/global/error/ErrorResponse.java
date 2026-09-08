package com.survey.meetorsolo.global.error;

import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import java.util.List;

/**
 * @param sanction 제재로 막힌 응답에만 채워지는 사유·기간 안내. 그 외에는 {@code null}
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fields,
        MemberSanctionNotice sanction
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fields) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fields, null);
    }

    public static ErrorResponse ofSanction(ErrorCode errorCode, MemberSanctionNotice sanction) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of(), sanction);
    }

    public record FieldError(
            String field,
            String message
    ) {
    }
}
