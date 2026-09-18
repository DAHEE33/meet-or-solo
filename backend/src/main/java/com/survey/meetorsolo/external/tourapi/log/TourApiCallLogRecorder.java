package com.survey.meetorsolo.external.tourapi.log;

import com.survey.meetorsolo.external.tourapi.config.TourApiProperties;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourApiCallLogRecorder {

    private final TourApiCallLogRepository repository;
    private final TourApiProperties properties;

    public TourApiCallLogRecorder(
            TourApiCallLogRepository repository,
            TourApiProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            String operationName,
            String requestKey,
            int statusCode,
            int responseTimeMs,
            int resultCount,
            OffsetDateTime calledAt
    ) {
        repository.save(TourApiCallLog.success(
                operationName,
                requestKey,
                properties.mobileApp(),
                statusCode,
                responseTimeMs,
                resultCount,
                calledAt
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String operationName,
            String requestKey,
            Integer statusCode,
            int responseTimeMs,
            TourApiClientException exception,
            OffsetDateTime calledAt
    ) {
        repository.save(TourApiCallLog.failure(
                operationName,
                requestKey,
                properties.mobileApp(),
                statusCode,
                responseTimeMs,
                safeErrorMessage(exception),
                calledAt
        ));
    }

    private String safeErrorMessage(TourApiClientException exception) {
        String message = "type=" + exception.getErrorType();
        if (exception.getRemoteCode() != null && !exception.getRemoteCode().isBlank()) {
            message += ", remoteCode=" + exception.getRemoteCode().trim();
        }
        return message;
    }
}
