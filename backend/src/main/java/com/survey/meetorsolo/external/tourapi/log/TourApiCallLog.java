package com.survey.meetorsolo.external.tourapi.log;

import com.survey.meetorsolo.global.time.SeoulDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "tour_api_call_logs")
public class TourApiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_name", nullable = false, length = 100)
    private String operationName;

    @Column(name = "request_key", length = 255)
    private String requestKey;

    @Column(name = "mobile_app", nullable = false, length = 50)
    private String mobileApp;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "result_count")
    private Integer resultCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "called_at", nullable = false)
    private OffsetDateTime calledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TourApiCallLog() {
    }

    static TourApiCallLog success(
            String operationName,
            String requestKey,
            String mobileApp,
            int statusCode,
            int responseTimeMs,
            int resultCount,
            OffsetDateTime calledAt
    ) {
        TourApiCallLog callLog = base(
                operationName,
                requestKey,
                mobileApp,
                statusCode,
                true,
                responseTimeMs,
                calledAt
        );
        callLog.resultCount = resultCount;
        return callLog;
    }

    static TourApiCallLog failure(
            String operationName,
            String requestKey,
            String mobileApp,
            Integer statusCode,
            int responseTimeMs,
            String errorMessage,
            OffsetDateTime calledAt
    ) {
        TourApiCallLog callLog = base(
                operationName,
                requestKey,
                mobileApp,
                statusCode,
                false,
                responseTimeMs,
                calledAt
        );
        callLog.errorMessage = limit(errorMessage, 1000);
        return callLog;
    }

    private static TourApiCallLog base(
            String operationName,
            String requestKey,
            String mobileApp,
            Integer statusCode,
            boolean success,
            int responseTimeMs,
            OffsetDateTime calledAt
    ) {
        if (responseTimeMs < 0) {
            throw new IllegalArgumentException("API 응답 시간은 0 이상이어야 합니다.");
        }
        TourApiCallLog callLog = new TourApiCallLog();
        callLog.operationName = required(operationName, "operationName", 100);
        callLog.requestKey = limit(requestKey, 255);
        callLog.mobileApp = required(mobileApp, "mobileApp", 50);
        callLog.statusCode = statusCode;
        callLog.success = success;
        callLog.responseTimeMs = responseTimeMs;
        callLog.calledAt = Objects.requireNonNull(calledAt, "calledAt");
        return callLog;
    }

    private static String required(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return limit(value.trim(), maxLength);
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @PrePersist
    void prePersist() {
        if (calledAt == null) {
            calledAt = SeoulDateTime.now();
        }
        createdAt = SeoulDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public String getMobileApp() {
        return mobileApp;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getCalledAt() {
        return calledAt;
    }
}
