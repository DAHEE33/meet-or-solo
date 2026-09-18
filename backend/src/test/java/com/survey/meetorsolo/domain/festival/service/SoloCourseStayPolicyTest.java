package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.festival.dto.SoloCourseType;
import org.junit.jupiter.api.Test;

class SoloCourseStayPolicyTest {

    private final SoloCourseStayPolicy policy = new SoloCourseStayPolicy();

    @Test
    void HALF는_240분_FULL은_480분이다() {
        assertThat(policy.budgetMinutes(SoloCourseType.HALF)).isEqualTo(240);
        assertThat(policy.budgetMinutes(SoloCourseType.FULL)).isEqualTo(480);
    }

    @Test
    void 도보시간은_67미터당_1분으로_올림하고_최소_1분이다() {
        assertThat(policy.walkMinutes(0)).isEqualTo(1);
        assertThat(policy.walkMinutes(1)).isEqualTo(1);
        assertThat(policy.walkMinutes(67)).isEqualTo(1);
        assertThat(policy.walkMinutes(68)).isEqualTo(2);
        assertThat(policy.walkMinutes(670)).isEqualTo(10);
    }

    @Test
    void 체류시간은_contentTypeId별_고정값이고_알수없는_타입은_기본값을_쓴다() {
        assertThat(policy.stayMinutes("12")).isEqualTo(60);
        assertThat(policy.stayMinutes("14")).isEqualTo(45);
        assertThat(policy.stayMinutes("28")).isEqualTo(90);
        assertThat(policy.stayMinutes("39")).isEqualTo(50);
        assertThat(policy.stayMinutes("99")).isEqualTo(60);
    }
}
