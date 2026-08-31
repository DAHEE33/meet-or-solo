package com.survey.meetorsolo.domain.matching.scoring;

import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.ACTIVE;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.FOOD;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.PHOTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 회원 단위 점수 분해 집계 검증.
 *
 * <p>3인 이상에서는 한 회원 안에서도 임베딩 pair와 fallback pair가 섞인다. 이 혼합 상황에서
 * "저장된 분해값 + 가중치로 총점을 재구성할 수 있는가"가 이 클래스의 핵심 검증 대상이다.
 */
class MemberScoreBreakdownTest {

    private static final BigDecimal JACCARD_WEIGHT = new BigDecimal("0.70");
    private static final BigDecimal EMBEDDING_WEIGHT = new BigDecimal("0.30");

    private final PairCompatibilityScorer scorer = new PairCompatibilityScorer(
            new TravelStyleScorer(), new EmbeddingScorer(), JACCARD_WEIGHT, EMBEDDING_WEIGHT);

    /** 회원 A: {PHOTO} + 벡터, 회원 B: {PHOTO, FOOD} + 벡터, 회원 C: {FOOD, ACTIVE} 벡터 없음 */
    private static final List<com.survey.meetorsolo.domain.member.entity.TravelStyleCode> A_TAGS = List.of(PHOTO);
    private static final List<com.survey.meetorsolo.domain.member.entity.TravelStyleCode> B_TAGS = List.of(PHOTO, FOOD);
    private static final List<com.survey.meetorsolo.domain.member.entity.TravelStyleCode> C_TAGS = List.of(FOOD, ACTIVE);
    private static final float[] A_VECTOR = {1.0f, 0.0f};
    private static final float[] B_VECTOR = {0.6f, 0.8f};

    @Test
    void 임베딩_pair가_하나도_없으면_cosine은_null이고_총점은_Jaccard와_같다() {
        MemberScoreBreakdown breakdown = MemberScoreBreakdown.of(List.of(
                scorer.scoreDetailed(A_TAGS, C_TAGS, null, null),
                scorer.scoreDetailed(A_TAGS, B_TAGS, null, null)));

        assertThat(breakdown.cosine()).isNull();
        assertThat(breakdown.embeddingApplied()).isFalse();
        assertThat(breakdown.embeddingPairCount()).isZero();
        assertThat(breakdown.total()).isEqualByComparingTo(breakdown.jaccard());
    }

    @Test
    void 전체_pair가_임베딩이면_실제_코사인_평균이_그대로_저장된다() {
        MemberScoreBreakdown breakdown = MemberScoreBreakdown.of(List.of(
                scorer.scoreDetailed(A_TAGS, B_TAGS, A_VECTOR, B_VECTOR),
                scorer.scoreDetailed(A_TAGS, B_TAGS, A_VECTOR, A_VECTOR)));

        // 코사인 60.00과 100.00의 평균
        assertThat(breakdown.cosine()).isEqualByComparingTo("80.00");
        assertThat(breakdown.embeddingApplied()).isTrue();
        assertThat(breakdown.embeddingPairCount()).isEqualTo(2);
    }

    @Test
    void 임베딩_pair와_fallback_pair가_섞이면_전체_pair를_분모로_집계한다() {
        // 회원 A: pair(A,B)는 임베딩, pair(A,C)는 C가 벡터를 갖지 않아 fallback
        MemberScoreBreakdown a = MemberScoreBreakdown.of(List.of(
                scorer.scoreDetailed(A_TAGS, B_TAGS, A_VECTOR, B_VECTOR),
                scorer.scoreDetailed(A_TAGS, C_TAGS, A_VECTOR, null)));

        // pair(A,B) Jaccard 50.00 / 코사인 60.00 -> 총점 53.00
        // pair(A,C) Jaccard  0.00 / fallback     -> 총점  0.00
        assertThat(a.jaccard()).isEqualByComparingTo("25.00");
        // fallback pair의 임베딩 항 투입값은 그 pair의 Jaccard(0.00)다 -> (60.00 + 0.00) / 2
        assertThat(a.cosine()).isEqualByComparingTo("30.00");
        assertThat(a.embeddingApplied()).isTrue();
        assertThat(a.embeddingPairCount()).isEqualTo(1);
        assertThat(a.total()).isEqualByComparingTo("26.50");
    }

    @Test
    void 혼합_회원도_분해값과_가중치로_총점을_재구성할_수_있다() {
        // 실제 코사인이 있는 pair만 평균하면 jaccard와 cosine의 분모가 달라져 재구성이 불가능해진다.
        // 전체 pair를 분모로 두는 정의라야 아래 항등식이 성립한다.
        MemberScoreBreakdown b = MemberScoreBreakdown.of(List.of(
                scorer.scoreDetailed(B_TAGS, A_TAGS, B_VECTOR, A_VECTOR),
                scorer.scoreDetailed(B_TAGS, C_TAGS, B_VECTOR, null)));

        // pair(B,A) Jaccard 50.00 / 코사인 60.00 -> 53.00
        // pair(B,C) Jaccard 33.33 / fallback     -> 33.33
        assertThat(b.jaccard()).isEqualByComparingTo("41.67");
        assertThat(b.cosine()).isEqualByComparingTo("46.67");
        assertThat(b.total()).isEqualByComparingTo("43.17");
        assertThat(reconstruct(b)).isEqualByComparingTo(b.total());
    }

    @Test
    void 혼합_그룹의_임베딩_미보유_회원은_cosine이_null이고_pair_count가_0이다() {
        // 회원 C는 벡터가 없으므로 어느 pair에서도 임베딩이 쓰이지 않는다.
        MemberScoreBreakdown c = MemberScoreBreakdown.of(List.of(
                scorer.scoreDetailed(C_TAGS, A_TAGS, null, A_VECTOR),
                scorer.scoreDetailed(C_TAGS, B_TAGS, null, B_VECTOR)));

        assertThat(c.jaccard()).isEqualByComparingTo("16.67");
        assertThat(c.cosine()).isNull();
        assertThat(c.embeddingApplied()).isFalse();
        assertThat(c.embeddingPairCount()).isZero();
        assertThat(c.total()).isEqualByComparingTo("16.67");
    }

    @Test
    void 네명_전원_보유는_pair_count가_3이_된다() {
        MemberScoreBreakdown breakdown = MemberScoreBreakdown.of(List.of(
                scorer.scoreDetailed(A_TAGS, B_TAGS, A_VECTOR, B_VECTOR),
                scorer.scoreDetailed(A_TAGS, C_TAGS, A_VECTOR, B_VECTOR),
                scorer.scoreDetailed(A_TAGS, B_TAGS, A_VECTOR, A_VECTOR)));

        assertThat(breakdown.embeddingPairCount()).isEqualTo(3);
        assertThat(breakdown.embeddingApplied()).isTrue();
        assertThat(reconstruct(breakdown)).isEqualByComparingTo(breakdown.total());
    }

    @Test
    void 총점은_pair_총점_평균이라_분해_저장_이전_계산과_같다() {
        // 기존 member_score 값이 바뀌지 않는지 확인한다.
        List<PairScore> pairScores = List.of(
                scorer.scoreDetailed(A_TAGS, B_TAGS, A_VECTOR, B_VECTOR),
                scorer.scoreDetailed(A_TAGS, C_TAGS, A_VECTOR, null));

        BigDecimal legacy = pairScores.stream()
                .map(PairScore::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(pairScores.size()),
                        PairCompatibilityScorer.SCORE_SCALE, RoundingMode.HALF_UP);

        assertThat(MemberScoreBreakdown.of(pairScores).total()).isEqualByComparingTo(legacy);
    }

    @Test
    void 서로_어긋나는_분해값은_생성_단계에서_거절한다() {
        assertThatThrownBy(() -> new MemberScoreBreakdown(
                new BigDecimal("10.00"), new BigDecimal("10.00"), null, true, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cosine");
        assertThatThrownBy(() -> new MemberScoreBreakdown(
                new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("10.00"), true, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingPairCount");
    }

    @Test
    void pair가_없으면_집계할_수_없다() {
        assertThatThrownBy(() -> MemberScoreBreakdown.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어 있을 수 없습니다");
    }

    private BigDecimal reconstruct(MemberScoreBreakdown breakdown) {
        if (breakdown.cosine() == null) {
            return breakdown.jaccard();
        }
        return breakdown.jaccard().multiply(JACCARD_WEIGHT)
                .add(breakdown.cosine().multiply(EMBEDDING_WEIGHT))
                .setScale(PairCompatibilityScorer.SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
