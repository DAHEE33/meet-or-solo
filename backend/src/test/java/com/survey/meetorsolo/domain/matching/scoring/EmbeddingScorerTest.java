package com.survey.meetorsolo.domain.matching.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EmbeddingScorerTest {

    private final EmbeddingScorer scorer = new EmbeddingScorer();

    @Test
    void 동일한_벡터는_100점을_반환한다() {
        float[] vec = {1.0f, 0.0f, 0.0f};
        BigDecimal score = scorer.score(vec, vec);
        assertThat(score).isEqualByComparingTo("100.00");
    }

    @Test
    void 직교하는_벡터는_0점을_반환한다() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        BigDecimal score = scorer.score(a, b);
        assertThat(score).isEqualByComparingTo("0.00");
    }

    @Test
    void 사십오도_벡터는_약_70점을_반환한다() {
        float[] a = {1.0f, 0.0f};
        float[] b = {1.0f, 1.0f};
        BigDecimal score = scorer.score(a, b);
        // cos(45°) = 1/√2 ≈ 0.7071 → 70.71점
        assertThat(score).isEqualByComparingTo("70.71");
    }

    @Test
    void 한쪽이_null이면_null을_반환한다() {
        float[] vec = {1.0f, 0.0f};
        assertThat(scorer.score(null, vec)).isNull();
        assertThat(scorer.score(vec, null)).isNull();
        assertThat(scorer.score(null, null)).isNull();
    }

    @Test
    void 빈_배열은_null을_반환한다() {
        assertThat(scorer.score(new float[]{}, new float[]{})).isNull();
    }

    @Test
    void 차원이_다르면_예외를_던진다() {
        float[] a = {1.0f, 0.0f};
        float[] b = {1.0f, 0.0f, 0.0f};
        assertThatThrownBy(() -> scorer.score(a, b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("임베딩 차원이 다릅니다");
    }

    @Test
    void 음수_코사인_유사도는_0으로_clamp된다() {
        // 반대 방향 벡터 → cos = -1 → clamp 후 0
        float[] a = {1.0f, 0.0f};
        float[] b = {-1.0f, 0.0f};
        BigDecimal score = scorer.score(a, b);
        assertThat(score).isEqualByComparingTo("0.00");
    }
}
