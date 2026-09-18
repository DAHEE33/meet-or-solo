package com.survey.meetorsolo.external.tourapi.exception;

import java.util.Objects;

public class TourApiClientException extends RuntimeException {

    private final TourApiErrorType errorType;
    private final String remoteCode;
    private final Integer httpStatus;

    public TourApiClientException(TourApiErrorType errorType) {
        this(errorType, null, null, null, null);
    }

    public TourApiClientException(TourApiErrorType errorType, Throwable cause) {
        this(errorType, null, null, null, cause);
    }

    public static TourApiClientException withDetail(
            TourApiErrorType errorType,
            String detail
    ) {
        return new TourApiClientException(errorType, detail, null, null, null);
    }

    public static TourApiClientException withDetail(
            TourApiErrorType errorType,
            String detail,
            Throwable cause
    ) {
        return new TourApiClientException(errorType, detail, null, null, cause);
    }

    public static TourApiClientException forRemoteError(
            TourApiErrorType errorType,
            String remoteCode
    ) {
        return new TourApiClientException(errorType, null, remoteCode, null, null);
    }

    public static TourApiClientException forHttpError(
            TourApiErrorType errorType,
            int httpStatus,
            Throwable cause
    ) {
        return new TourApiClientException(errorType, null, null, httpStatus, cause);
    }

    private TourApiClientException(
            TourApiErrorType errorType,
            String detail,
            String remoteCode,
            Integer httpStatus,
            Throwable cause
    ) {
        super(buildMessage(errorType, detail), cause);
        this.errorType = errorType;
        this.remoteCode = remoteCode;
        this.httpStatus = httpStatus;
    }

    private static String buildMessage(TourApiErrorType errorType, String detail) {
        String defaultMessage = Objects.requireNonNull(errorType, "errorType")
                .getDefaultMessage();
        if (detail == null || detail.isBlank()) {
            return defaultMessage;
        }
        return defaultMessage + " " + detail.trim();
    }

    public TourApiErrorType getErrorType() {
        return errorType;
    }

    public String getRemoteCode() {
        return remoteCode;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
