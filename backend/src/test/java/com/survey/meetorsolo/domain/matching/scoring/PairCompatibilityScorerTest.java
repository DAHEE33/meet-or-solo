package com.survey.meetorsolo.domain.matching.scoring;

import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.ACTIVE;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.CULTURE;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.FOOD;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.PHOTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PairCompatibilityScorerTest {

    private static final BigDecimal JACCARD_WEIGHT = new BigDecimal("0.70");
    private static final BigDecimal EMBEDDING_WEIGHT = new BigDecimal("0.30");

    private final PairCompatibilityScorer scorer = new PairCompatibilityScorer(
            new TravelStyleScorer(), new EmbeddingScorer(), JACCARD_WEIGHT, EMBEDDING_WEIGHT);

    @Test
    void 임베딩이_양쪽에_있으면_Jaccard_70과_코사인_30을_가중_합산한다() {
        // 태그 완전 일치 -> Jaccard 100, 임베딩 직교 -> 코사인 0
        BigDecimal score = scorer.score(
                List.of(FOOD), List.of(FOOD),
                new float[] {1.0f, 0.0f}, new float[] {0.0f, 1.0f});

        // 100 * 0.70 + 0 * 0.30 = 70.00
        assertThat(score).isEqualByComparingTo("70.00");
    }

    @Test
    void 태그가_전혀_겹치지_않아도_임베딩이_같으면_임베딩_비중만큼_점수를_얻는다() {
        BigDecimal score = scorer.score(
                List.of(FOOD), List.of(ACTIVE),
                new float[] {1.0f, 0.0f}, new float[] {1.0f, 0.0f});

        // 0 * 0.70 + 100 * 0.30 = 30.00
        assertThat(score).isEqualByComparingTo("30.00");
    }

    @Test
    void 한쪽이라도_임베딩이_없으면_Jaccard_점수만_사용한다() {
        BigDecimal withoutRight = scorer.score(
                List.of(FOOD, PHOTO), List.of(FOOD, ACTIVE),
                new float[] {1.0f, 0.0f}, null);
        BigDecimal withoutBoth = scorer.score(
                List.of(FOOD, PHOTO), List.of(FOOD, ACTIVE), null, null);

        // 교집합 1 / 합집합 3 = 33.33
        assertThat(withoutRight).isEqualByComparingTo("33.33");
        assertThat(withoutBoth).isEqualByComparingTo("33.33");
    }

    @Test
    void 임베딩_미보유_fallback은_코사인을_Jaccard로_간주한_결과와_같다() {
        // 임베딩 보유자와 미보유자가 섞여도 점수 스케일이 왜곡되지 않는지 확인한다.
        BigDecimal fallback = scorer.score(List.of(FOOD, PHOTO), List.of(FOOD, ACTIVE), null, null);
        BigDecimal jaccardOnly = new TravelStyleScorer()
                .score(List.of(FOOD, PHOTO), List.of(FOOD, ACTIVE));

        assertThat(fallback).isEqualByComparingTo(jaccardOnly);
    }

    @Test
    void 태그가_완전히_갈리면_임베딩으로_순위를_뒤집을_수_없다() {
        // 태그 불일치 + 임베딩 완전 일치
        BigDecimal tagMismatch = scorer.score(
                List.of(FOOD), List.of(ACTIVE),
                new float[] {1.0f, 0.0f}, new float[] {1.0f, 0.0f});
        // 태그 완전 일치 + 임베딩 직교
        BigDecimal tagMatch = scorer.score(
                List.of(FOOD), List.of(FOOD),
                new float[] {1.0f, 0.0f}, new float[] {0.0f, 1.0f});

        assertThat(tagMatch).isGreaterThan(tagMismatch);
    }

    @Test
    void 태그가_비슷하면_임베딩이_순위를_결정한다() {
        // 같은 Jaccard 버킷(교집합 1 / 합집합 3 = 33.33)에서 임베딩만 다른 두 pair
        BigDecimal similarText = scorer.score(
                List.of(FOOD, PHOTO), List.of(FOOD, ACTIVE),
                new float[] {1.0f, 0.0f}, new float[] {1.0f, 0.0f});
        BigDecimal differentText = scorer.score(
                List.of(FOOD, PHOTO), List.of(FOOD, CULTURE),
                new float[] {1.0f, 0.0f}, new float[] {0.0f, 1.0f});

        assertThat(similarText).isGreaterThan(differentText);
    }

    @Test
    void 가중치_합이_1이_아니면_생성에_실패한다() {
        assertThatThrownBy(() -> new PairCompatibilityScorer(
                new TravelStyleScorer(), new EmbeddingScorer(),
                new BigDecimal("0.70"), new BigDecimal("0.40")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가중치 합은 1이어야 합니다");
    }

    @Test
    void 음수_가중치는_거절한다() {
        assertThatThrownBy(() -> new PairCompatibilityScorer(
                new TravelStyleScorer(), new EmbeddingScorer(),
                new BigDecimal("1.20"), new BigDecimal("-0.20")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가중치는 0 이상이어야 합니다");
    }

    @Test
    void 설정된_가중치를_바꾸면_결합_비중도_바뀐다() {
        PairCompatibilityScorer embeddingFirst = new PairCompatibilityScorer(
                new TravelStyleScorer(), new EmbeddingScorer(),
                new BigDecimal("0.30"), new BigDecimal("0.70"));

        BigDecimal score = embeddingFirst.score(
                List.of(FOOD), List.of(ACTIVE),
                new float[] {1.0f, 0.0f}, new float[] {1.0f, 0.0f});

        // 0 * 0.30 + 100 * 0.70 = 70.00
        assertThat(score).isEqualByComparingTo("70.00");
    }

    @Test
    void scoreDetailed는_임베딩_pair의_Jaccard와_실제_코사인을_함께_반환한다() {
        PairScore pairScore = scorer.scoreDetailed(
                List.of(FOOD, PHOTO), List.of(FOOD, ACTIVE),
                new float[] {1.0f, 0.0f}, new float[] {0.6f, 0.8f});

        // Jaccard 교집합 1 / 합집합 3 = 33.33, 코사인 0.60 -> 60.00
        assertThat(pairScore.jaccard()).isEqualByComparingTo("33.33");
        assertThat(pairScore.cosine()).isEqualByComparingTo("60.00");
        assertThat(pairScore.embeddingApplied()).isTrue();
        // 33.33 * 0.70 + 60.00 * 0.30 = 41.331 -> 41.33
        assertThat(pairScore.total()).isEqualByComparingTo("41.33");
    }

    @Test
    void scoreDetailed는_fallback_pair의_코사인을_Jaccard로_채우고_미적용으로_표시한다() {
        // 저장된 분해값만으로 총점을 재구성할 수 있게 하려는 정의다.
        // 실제 임베딩이 쓰였는지는 embeddingApplied로만 구분한다.
        PairScore pairScore = scorer.scoreDetailed(
                List.of(FOOD, PHOTO), List.of(FOOD, ACTIVE),
                new float[] {1.0f, 0.0f}, null);

        assertThat(pairScore.jaccard()).isEqualByComparingTo("33.33");
        assertThat(pairScore.cosine()).isEqualByComparingTo("33.33");
        assertThat(pairScore.embeddingApplied()).isFalse();
        assertThat(pairScore.total()).isEqualByComparingTo("33.33");
    }

    @Test
    void scoreDetailed의_total은_어떤_조합에서도_score와_같다() {
        // 그룹 선정 점수(score)와 저장 점수(scoreDetailed)가 갈라지지 않는지 확인한다.
        float[] left = {1.0f, 0.0f};
        float[] right = {0.6f, 0.8f};
        List<List<TravelStyleCode>> tagPairs = List.of(
                List.of(FOOD), List.of(FOOD, PHOTO), List.of(ACTIVE, CULTURE));
        List<float[]> embeddings = new ArrayList<>();
        embeddings.add(left);
        embeddings.add(right);
        embeddings.add(null);

        for (List<TravelStyleCode> leftTags : tagPairs) {
            for (List<TravelStyleCode> rightTags : tagPairs) {
                for (float[] leftEmbedding : embeddings) {
                    for (float[] rightEmbedding : embeddings) {
                        assertThat(scorer.scoreDetailed(leftTags, rightTags, leftEmbedding, rightEmbedding).total())
                                .isEqualByComparingTo(
                                        scorer.score(leftTags, rightTags, leftEmbedding, rightEmbedding));
                    }
                }
            }
        }
    }

    @Test
    void 임베딩_pair는_분해값과_가중치로_총점을_재구성할_수_있다() {
        PairScore pairScore = scorer.scoreDetailed(
                List.of(FOOD), List.of(FOOD),
                new float[] {1.0f, 0.0f}, new float[] {0.8f, 0.6f});

        BigDecimal reconstructed = pairScore.jaccard().multiply(JACCARD_WEIGHT)
                .add(pairScore.cosine().multiply(EMBEDDING_WEIGHT))
                .setScale(PairCompatibilityScorer.SCORE_SCALE, java.math.RoundingMode.HALF_UP);

        assertThat(reconstructed).isEqualByComparingTo(pairScore.total());
    }
}
