package com.survey.meetorsolo.domain.matching.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * 한 회원이 낀 모든 pair를 집계한 회원 단위 점수와 그 분해값. {@code match_attempt_members}에 저장된다.
 *
 * <p>3인 이상에서는 한 회원 안에서도 어떤 pair는 임베딩을 쓰고 어떤 pair는 fallback인 혼합 상황이
 * 생긴다. 이때 "실제 코사인이 있는 pair만 평균"하면 {@code jaccard}와 {@code cosine}의 분모가 서로
 * 달라져 총점을 재구성할 수 없다. 그래서 두 값 모두 <b>전체 pair</b>를 분모로 두고, fallback pair는
 * {@link PairScore}의 정의대로 Jaccard를 임베딩 항 투입값으로 본다. 그 결과 아래가 항상 성립한다.
 *
 * <pre>
 *   cosine == null  ->  total = jaccard
 *   cosine != null  ->  total = jaccardWeight * jaccard + embeddingWeight * cosine
 * </pre>
 *
 * <p>다만 pair 점수가 pair마다 반올림된 뒤 평균되므로 3~4인에서는 위 항등식이 0.01 정도 어긋날 수
 * 있다. 기존 {@code member_score} 계산 순서를 바꾸지 않는 한 제거할 수 없는 오차다.
 *
 * <p>{@code cosine}이 혼합 pair에서는 일부가 Jaccard에서 온 합성값이므로 "실제 임베딩 신호"로 바로
 * 읽으면 안 된다. 혼합 비율은 {@link #embeddingPairCount()}로 판별한다. 회원당 전체 pair 수는
 * {@code match_attempts.target_group_size - 1}로 복원된다.
 *
 * @param total              회원 점수. pair 총점의 평균이며 기존 {@code member_score}와 같은 값이다
 * @param jaccard            전체 pair의 Jaccard 평균
 * @param cosine             전체 pair의 임베딩 항 투입값 평균. 임베딩 pair가 없으면 null
 * @param embeddingApplied   임베딩 pair가 1개 이상인지 여부. {@code cosine != null}과 동치다
 * @param embeddingPairCount 실제 임베딩이 적용된 pair 수
 */
public record MemberScoreBreakdown(
        BigDecimal total,
        BigDecimal jaccard,
        BigDecimal cosine,
        boolean embeddingApplied,
        int embeddingPairCount
) {

    public MemberScoreBreakdown {
        Objects.requireNonNull(total, "total은 필수입니다.");
        Objects.requireNonNull(jaccard, "jaccard는 필수입니다.");
        if (embeddingPairCount < 0) {
            throw new IllegalArgumentException("embeddingPairCount는 0 이상이어야 합니다.");
        }
        if (embeddingApplied != (cosine != null)) {
            throw new IllegalArgumentException("embeddingApplied와 cosine 보유 여부가 일치해야 합니다.");
        }
        if (embeddingApplied != (embeddingPairCount > 0)) {
            throw new IllegalArgumentException("embeddingApplied와 embeddingPairCount가 일치해야 합니다.");
        }
    }

    /** 회원이 낀 pair 점수들을 집계한다. */
    public static MemberScoreBreakdown of(Collection<PairScore> pairScores) {
        Objects.requireNonNull(pairScores, "pairScores는 필수입니다.");
        if (pairScores.isEmpty()) {
            throw new IllegalArgumentException("pairScores는 비어 있을 수 없습니다.");
        }
        BigDecimal totalSum = BigDecimal.ZERO;
        BigDecimal jaccardSum = BigDecimal.ZERO;
        BigDecimal cosineSum = BigDecimal.ZERO;
        int embeddingPairCount = 0;
        for (PairScore pairScore : pairScores) {
            Objects.requireNonNull(pairScore, "pairScores에는 null을 포함할 수 없습니다.");
            totalSum = totalSum.add(pairScore.total());
            jaccardSum = jaccardSum.add(pairScore.jaccard());
            cosineSum = cosineSum.add(pairScore.cosine());
            if (pairScore.embeddingApplied()) {
                embeddingPairCount++;
            }
        }
        BigDecimal pairCount = BigDecimal.valueOf(pairScores.size());
        boolean embeddingApplied = embeddingPairCount > 0;
        return new MemberScoreBreakdown(
                average(totalSum, pairCount),
                average(jaccardSum, pairCount),
                embeddingApplied ? average(cosineSum, pairCount) : null,
                embeddingApplied,
                embeddingPairCount
        );
    }

    private static BigDecimal average(BigDecimal sum, BigDecimal pairCount) {
        return sum.divide(pairCount, PairCompatibilityScorer.SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
