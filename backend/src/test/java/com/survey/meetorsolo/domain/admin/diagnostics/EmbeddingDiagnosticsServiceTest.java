package com.survey.meetorsolo.domain.admin.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.admin.diagnostics.dto.EmbeddingDiagnosticsResponse;
import com.survey.meetorsolo.domain.admin.diagnostics.service.EmbeddingDiagnosticsService;
import com.survey.meetorsolo.external.openai.EmbeddingFailedException;
import com.survey.meetorsolo.external.openai.EmbeddingFailureReason;
import com.survey.meetorsolo.external.openai.OpenAiEmbeddingClient;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingDiagnosticsServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), SeoulDateTime.ZONE_ID);

    @Mock
    private OpenAiEmbeddingClient client;

    private EmbeddingDiagnosticsService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingDiagnosticsService(client, CLOCK);
    }

    @Test
    void 왕복에_성공하면_차원과_모델을_함께_돌려준다() {
        when(client.embed(anyString())).thenReturn(new float[1536]);
        when(client.getModel()).thenReturn("text-embedding-3-small");
        when(client.isApiKeyPresent()).thenReturn(true);

        EmbeddingDiagnosticsResponse response = service.probe();

        assertThat(response.ok()).isTrue();
        assertThat(response.reason()).isNull();
        assertThat(response.dimensions()).isEqualTo(1536);
        assertThat(response.model()).isEqualTo("text-embedding-3-small");
        assertThat(response.apiKeyPresent()).isTrue();
    }

    @Test
    void 실패해도_예외를_던지지_않고_이유를_담아_돌려준다() {
        // 실패 이유가 곧 진단 결과다. 예외로 500을 내면 화면에서 원인을 볼 수 없다.
        when(client.embed(anyString()))
                .thenThrow(new EmbeddingFailedException(EmbeddingFailureReason.CONNECT_FAILED));

        EmbeddingDiagnosticsResponse response = service.probe();

        assertThat(response.ok()).isFalse();
        assertThat(response.reason()).isEqualTo("CONNECT_FAILED");
        assertThat(response.dimensions()).isZero();
    }

    @Test
    void 분류되지_않은_예외는_UNKNOWN으로_남긴다() {
        when(client.embed(anyString())).thenThrow(new IllegalStateException("예상 못 한 오류"));

        assertThat(service.probe().reason()).isEqualTo("UNKNOWN");
    }
}
