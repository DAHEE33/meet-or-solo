package com.survey.meetorsolo.domain.admin.diagnostics.dto;

import java.time.OffsetDateTime;

/**
 * 임베딩 연결 진단 결과.
 *
 * @param ok            임베딩을 실제로 받아왔는지
 * @param reason        실패 이유({@code EmbeddingFailureReason}). 성공이면 null
 * @param apiKeyPresent API key가 주입돼 있는지. 값 자체는 담지 않는다
 * @param model         호출에 쓴 모델 이름
 * @param dimensions    받아온 벡터 차원. 실패면 0
 * @param elapsedMs     호출에 걸린 시간. 타임아웃 판정을 눈으로 확인할 때 쓴다
 * @param checkedAt     진단 시각
 */
public record EmbeddingDiagnosticsResponse(
        boolean ok,
        String reason,
        boolean apiKeyPresent,
        String model,
        int dimensions,
        long elapsedMs,
        OffsetDateTime checkedAt
) {
}
