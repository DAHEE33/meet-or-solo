package com.survey.meetorsolo.external.tourapi.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.external.tourapi.config.TourApiProperties;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TourApiCallLogRecorderTest {

    private TourApiCallLogRepository repository;
    private TourApiCallLogRecorder recorder;

    @BeforeEach
    void setUp() {
        repository = mock(TourApiCallLogRepository.class);
        when(repository.save(any(TourApiCallLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        recorder = new TourApiCallLogRecorder(repository, new TourApiProperties(
                "https://example.com",
                "secret-key",
                "ETC",
                "SoloIn",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        ));
    }

    @Test
    void 성공_호출의_상태와_결과_건수를_저장한다() {
        OffsetDateTime calledAt = OffsetDateTime.parse("2026-07-18T10:00:00+09:00");

        recorder.recordSuccess("searchFestival2", "page=1", 200, 120, 5, calledAt);

        TourApiCallLog saved = capturedLog();
        assertThat(saved.getOperationName()).isEqualTo("searchFestival2");
        assertThat(saved.getRequestKey()).isEqualTo("page=1");
        assertThat(saved.getMobileApp()).isEqualTo("SoloIn");
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.isSuccess()).isTrue();
        assertThat(saved.getResponseTimeMs()).isEqualTo(120);
        assertThat(saved.getResultCount()).isEqualTo(5);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getCalledAt()).isEqualTo(calledAt);
    }

    @Test
    void 실패_로그에는_예외_상세나_API_Key를_저장하지_않는다() {
        TourApiClientException exception = TourApiClientException.withDetail(
                TourApiErrorType.NETWORK,
                "serviceKey=secret-key",
                new IllegalStateException("https://example.com?serviceKey=secret-key")
        );

        recorder.recordFailure(
                "searchFestival2",
                "page=1",
                null,
                500,
                exception,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00")
        );

        TourApiCallLog saved = capturedLog();
        assertThat(saved.isSuccess()).isFalse();
        assertThat(saved.getStatusCode()).isNull();
        assertThat(saved.getResultCount()).isNull();
        assertThat(saved.getErrorMessage()).isEqualTo("type=NETWORK");
        assertThat(saved.getErrorMessage()).doesNotContain("secret-key", "serviceKey", "https://");
    }

    private TourApiCallLog capturedLog() {
        ArgumentCaptor<TourApiCallLog> captor = ArgumentCaptor.forClass(TourApiCallLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
