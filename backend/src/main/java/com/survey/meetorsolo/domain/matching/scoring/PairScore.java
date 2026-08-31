package com.survey.meetorsolo.domain.matching.scoring;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 두 회원 pair의 궁합 점수와 그 분해값.
 *
 * <p>{@code cosine}은 "임베딩 항에 실제로 투입된 값"이다. 양쪽 모두 임베딩을 보유하면 실제 코사인
 * 유사도이고, 한쪽이라도 없으면(fallback) 같은 pair의 Jaccard 점수가 들어간다. fallback이
 * {@code cosine = jaccard}와 수학적으로 같다는 기존 원칙을 저장 정의에도 그대로 적용한 것이며,
 * 덕분에 {@code total = w_j * jaccard + w_e * cosine}이 fallback 여부와 무관하게 성립한다.
 *
 * <p>실제 임베딩이 쓰였는지는 {@link #embeddingApplied()}로 구분한다.
 *
 * @param jaccard          여행스타일 태그 Jaccard 점수 (0~100)
 * @param cosine           임베딩 항 투입값 (0~100). fallback pair는 {@code jaccard}와 같다
 * @param embeddingApplied 양쪽 모두 임베딩을 보유해 실제 코사인 유사도가 쓰였는지 여부
 * @param total            저장·정렬에 쓰이는 최종 pair 점수 (0~100)
 */
public record PairScore(
        BigDecimal jaccard,
        BigDecimal cosine,
        boolean embeddingApplied,
        BigDecimal total
) {

    public PairScore {
        Objects.requireNonNull(jaccard, "jaccard는 필수입니다.");
        Objects.requireNonNull(cosine, "cosine은 필수입니다.");
        Objects.requireNonNull(total, "total은 필수입니다.");
    }
}
