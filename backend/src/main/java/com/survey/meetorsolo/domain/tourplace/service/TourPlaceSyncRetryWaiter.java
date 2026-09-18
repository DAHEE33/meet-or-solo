package com.survey.meetorsolo.domain.tourplace.service;

import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class TourPlaceSyncRetryWaiter {

    public void waitFor(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw TourApiClientException.withDetail(
                    TourApiErrorType.NETWORK,
                    "재시도 대기가 중단되었습니다.",
                    exception
            );
        }
    }
}
