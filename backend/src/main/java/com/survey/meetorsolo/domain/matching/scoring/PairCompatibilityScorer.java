package com.survey.meetorsolo.domain.matching.scoring;

import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * 두 회원의 최종 궁합 점수(0~100)를 계산한다.
 *
 * <p>여행스타일 태그 Jaccard 점수와 취향 자유 입력 임베딩의 코사인 유사도를 가중 합산한다.
 * 그룹 선정 점수와 proposal에 저장되는 회원 점수가 갈라지지 않도록 계산식을 이 클래스로 모은다.
 *
 * <p>가중치 기준: 태그는 5종 중 1~3개만 고르므로 Jaccard가 취할 수 있는 값이
 * {@code 0, 20, 25, 33.33, 50, 66.67, 100} 7가지뿐이고 동점이 자주 발생한다. 반면 코사인 유사도는
 * 촘촘하지만 무관한 텍스트도 값이 낮게 내려가지 않는다. 태그를 주 신호로 두고 임베딩이 태그 동점을
 * 가르는 배분이 필요하므로 초기값을 Jaccard 0.70 / 임베딩 0.30으로 둔다. 이 배분에서는 태그가 크게
 * 갈리면(예: 0 vs 100) 임베딩이 순위를 뒤집을 수 없고, 태그가 비슷하면 임베딩이 순위를 결정한다.
 * 실사용 매칭 데이터를 확보한 뒤 재조정할 값이므로 설정으로 주입받는다.
 */
public class PairCompatibilityScorer {

    public static final int SCORE_SCALE = 2;

    private final TravelStyleScorer travelStyleScorer;
    private final EmbeddingScorer embeddingScorer;
    private final BigDecimal jaccardWeight;
    private final BigDecimal embeddingWeight;

    public PairCompatibilityScorer(
            TravelStyleScorer travelStyleScorer,
            EmbeddingScorer embeddingScorer,
            BigDecimal jaccardWeight,
            BigDecimal embeddingWeight
    ) {
        this.travelStyleScorer = Objects.requireNonNull(travelStyleScorer, "travelStyleScorer는 필수입니다.");
        this.embeddingScorer = Objects.requireNonNull(embeddingScorer, "embeddingScorer는 필수입니다.");
        this.jaccardWeight = Objects.requireNonNull(jaccardWeight, "jaccardWeight는 필수입니다.");
        this.embeddingWeight = Objects.requireNonNull(embeddingWeight, "embeddingWeight는 필수입니다.");
        if (jaccardWeight.signum() < 0 || embeddingWeight.signum() < 0) {
            throw new IllegalArgumentException("가중치는 0 이상이어야 합니다.");
        }
        if (jaccardWeight.add(embeddingWeight).compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException(
                    "가중치 합은 1이어야 합니다: " + jaccardWeight + " + " + embeddingWeight);
        }
    }

    /**
     * 두 회원의 궁합 점수를 0~100 범위로 반환한다.
     *
     * <p>양쪽 모두 완료된 임베딩을 가진 경우에만 가중 합산한다. 한쪽이라도 임베딩이 없으면 Jaccard
     * 점수만 사용한다. 이 fallback은 {@code cosine = jaccard}로 간주하는 것과 결과가 같으므로
     * 임베딩 보유자와 미보유자가 같은 후보 pool에 섞여도 점수 스케일이 왜곡되지 않는다.
     *
     * <p>분해값이 필요하면 {@link #scoreDetailed}를 쓴다. 그룹 선정 점수와 저장 점수가 갈라지지
     * 않도록 이 메서드는 {@code scoreDetailed(...).total()}만 반환한다.
     *
     * @param leftEmbedding  임베딩 미보유 또는 미완료 회원은 null
     * @param rightEmbedding 임베딩 미보유 또는 미완료 회원은 null
     */
    public BigDecimal score(
            Collection<TravelStyleCode> leftStyles,
            Collection<TravelStyleCode> rightStyles,
            float[] leftEmbedding,
            float[] rightEmbedding
    ) {
        return scoreDetailed(leftStyles, rightStyles, leftEmbedding, rightEmbedding).total();
    }

    /**
     * {@link #score}와 같은 계산을 수행하되 총점과 함께 분해값을 반환한다.
     *
     * <p>fallback pair는 임베딩 항 투입값을 Jaccard로 둔다. 이렇게 하면 fallback 여부와 무관하게
     * {@code total = w_j * jaccard + w_e * cosine}이 성립해 저장된 값만으로 총점을 재구성할 수 있다.
     * 실제 임베딩이 쓰였는지는 {@link PairScore#embeddingApplied()}로 구분한다.
     */
    public PairScore scoreDetailed(
            Collection<TravelStyleCode> leftStyles,
            Collection<TravelStyleCode> rightStyles,
            float[] leftEmbedding,
            float[] rightEmbedding
    ) {
        BigDecimal jaccard = travelStyleScorer.score(leftStyles, rightStyles);
        BigDecimal cosine = embeddingScorer.score(leftEmbedding, rightEmbedding);
        if (cosine == null) {
            BigDecimal fallback = jaccard.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
            return new PairScore(fallback, fallback, false, fallback);
        }
        BigDecimal total = jaccard.multiply(jaccardWeight)
                .add(cosine.multiply(embeddingWeight))
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        return new PairScore(
                jaccard.setScale(SCORE_SCALE, RoundingMode.HALF_UP),
                cosine.setScale(SCORE_SCALE, RoundingMode.HALF_UP),
                true,
                total
        );
    }

    /** 현재 적용된 Jaccard 가중치. 로그·테스트 확인용. */
    public BigDecimal jaccardWeight() {
        return jaccardWeight;
    }

    /** 현재 적용된 임베딩 가중치. 로그·테스트 확인용. */
    public BigDecimal embeddingWeight() {
        return embeddingWeight;
    }
}
