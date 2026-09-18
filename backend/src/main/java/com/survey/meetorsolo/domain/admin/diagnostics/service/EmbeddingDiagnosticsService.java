package com.survey.meetorsolo.domain.admin.diagnostics.service;

import com.survey.meetorsolo.domain.admin.diagnostics.dto.EmbeddingDiagnosticsResponse;
import com.survey.meetorsolo.external.openai.EmbeddingFailedException;
import com.survey.meetorsolo.external.openai.EmbeddingFailureReason;
import com.survey.meetorsolo.external.openai.OpenAiEmbeddingClient;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 임베딩 연결이 지금 살아 있는지 관리자가 직접 확인하는 진단.
 *
 * <p>회원 저장 흐름에서는 실패가 조용히 {@code FAILED}로만 남는다. 그래서 "로컬은 되는데 서버는
 * 안 된다"를 확인하려면 서버에 붙어 로그를 봐야 했다. 이 진단은 회원 데이터를 건드리지 않고
 * 고정 문장 하나로 왕복만 시켜, 실패 이유를 화면에서 바로 보여준다.
 *
 * <p>회원 취향 데이터를 쓰지 않으므로 개인정보가 외부로 나가지 않고, DB에도 쓰지 않는다.
 */
@Service
public class EmbeddingDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDiagnosticsService.class);

    /** 진단 전용 고정 문장. 회원이 입력한 취향 글을 쓰지 않는다. */
    private static final String PROBE_TEXT = "연결 확인용 문장입니다.";

    private final OpenAiEmbeddingClient client;
    private final Clock clock;

    public EmbeddingDiagnosticsService(OpenAiEmbeddingClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public EmbeddingDiagnosticsResponse probe() {
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        long startedNanos = System.nanoTime();
        try {
            float[] vector = client.embed(PROBE_TEXT);
            return result(true, null, vector.length, startedNanos, startedAt);
        } catch (RuntimeException exception) {
            EmbeddingFailureReason reason = exception instanceof EmbeddingFailedException failed
                    ? failed.getReason()
                    : EmbeddingFailureReason.UNKNOWN;
            log.warn("임베딩 진단 실패. reason={}, error={}", reason, exception.getMessage());
            return result(false, reason.name(), 0, startedNanos, startedAt);
        }
    }

    private EmbeddingDiagnosticsResponse result(
            boolean ok,
            String reason,
            int dimensions,
            long startedNanos,
            OffsetDateTime checkedAt
    ) {
        return new EmbeddingDiagnosticsResponse(
                ok,
                reason,
                client.isApiKeyPresent(),
                client.getModel(),
                dimensions,
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                checkedAt
        );
    }
}
