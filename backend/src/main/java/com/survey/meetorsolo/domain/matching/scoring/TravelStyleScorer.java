package com.survey.meetorsolo.domain.matching.scoring;

import com.survey.meetorsolo.domain.member.entity.TravelStyleCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class TravelStyleScorer {

    public static final int SCORE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal score(
            Collection<TravelStyleCode> leftStyles,
            Collection<TravelStyleCode> rightStyles
    ) {
        Set<TravelStyleCode> left = normalize(leftStyles, "leftStyles");
        Set<TravelStyleCode> right = normalize(rightStyles, "rightStyles");
        if (left.isEmpty() || right.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCORE_SCALE);
        }

        Set<TravelStyleCode> intersection = EnumSet.copyOf(left);
        intersection.retainAll(right);
        Set<TravelStyleCode> union = EnumSet.copyOf(left);
        union.addAll(right);

        return BigDecimal.valueOf(intersection.size())
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(union.size()), SCORE_SCALE, RoundingMode.HALF_UP);
    }

    private Set<TravelStyleCode> normalize(
            Collection<TravelStyleCode> styles,
            String parameterName
    ) {
        Objects.requireNonNull(styles, parameterName + "는 필수입니다.");
        if (styles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(parameterName + "에는 null을 포함할 수 없습니다.");
        }
        if (styles.isEmpty()) {
            return EnumSet.noneOf(TravelStyleCode.class);
        }
        return EnumSet.copyOf(styles);
    }
}
