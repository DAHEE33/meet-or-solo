package com.survey.meetorsolo.domain.matching.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class EmbeddingScorer {

    public static final int SCORE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * 두 임베딩 벡터의 코사인 유사도를 0~100 범위의 BigDecimal로 반환한다.
     * 코사인 유사도는 -1~1이지만, 텍스트 임베딩은 보통 0~1이므로 0 미만은 0으로 clamp한다.
     * 어느 한쪽이 null이면 null을 반환한다 (fallback 신호).
     */
    public BigDecimal score(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return null;
        }
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "임베딩 차원이 다릅니다: " + left.length + " vs " + right.length);
        }

        double dotProduct = 0.0;
        double normLeft = 0.0;
        double normRight = 0.0;
        for (int i = 0; i < left.length; i++) {
            dotProduct += (double) left[i] * right[i];
            normLeft += (double) left[i] * left[i];
            normRight += (double) right[i] * right[i];
        }

        double denominator = Math.sqrt(normLeft) * Math.sqrt(normRight);
        if (denominator == 0.0) {
            return BigDecimal.ZERO.setScale(SCORE_SCALE);
        }

        double cosine = dotProduct / denominator;
        double clamped = Math.max(0.0, Math.min(1.0, cosine));

        return BigDecimal.valueOf(clamped)
                .multiply(ONE_HUNDRED)
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
