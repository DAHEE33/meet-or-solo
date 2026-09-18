package com.survey.meetorsolo.domain.matching.scoring;

import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.ACTIVE;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.FOOD;
import static com.survey.meetorsolo.domain.member.entity.TravelStyleCode.PHOTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TravelStyleScorerTest {

    private final TravelStyleScorer scorer = new TravelStyleScorer();

    @Test
    void 동일_집합은_100점이다() {
        assertThat(scorer.score(List.of(PHOTO, FOOD), List.of(PHOTO, FOOD)))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void 일부_교집합은_Jaccard_점수로_계산한다() {
        assertThat(scorer.score(List.of(PHOTO, FOOD), List.of(PHOTO, ACTIVE)))
                .isEqualByComparingTo("33.33");
    }

    @Test
    void 교집합이_없으면_0점이다() {
        assertThat(scorer.score(List.of(PHOTO), List.of(FOOD)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void 한쪽_또는_양쪽이_비어_있으면_0점이다() {
        assertThat(scorer.score(List.of(), List.of(PHOTO))).isEqualByComparingTo("0.00");
        assertThat(scorer.score(List.of(PHOTO), List.of())).isEqualByComparingTo("0.00");
        assertThat(scorer.score(List.of(), List.of())).isEqualByComparingTo("0.00");
    }

    @Test
    void 순서와_중복은_점수에_영향을_주지_않는다() {
        assertThat(scorer.score(
                List.of(PHOTO, FOOD, PHOTO),
                List.of(ACTIVE, PHOTO, ACTIVE)
        )).isEqualByComparingTo("33.33");
    }

    @Test
    void null_입력과_null_코드는_거절한다() {
        assertThatThrownBy(() -> scorer.score(null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("leftStyles는 필수입니다.");
        assertThatThrownBy(() -> scorer.score(List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rightStyles는 필수입니다.");
        assertThatThrownBy(() -> scorer.score(java.util.Arrays.asList(PHOTO, null), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("leftStyles에는 null을 포함할 수 없습니다.");
    }
}
