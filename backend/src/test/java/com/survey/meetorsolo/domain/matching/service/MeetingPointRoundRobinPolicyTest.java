package com.survey.meetorsolo.domain.matching.service;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeetingPointRoundRobinPolicyTest {
    private final MeetingPointRoundRobinPolicy policy = new MeetingPointRoundRobinPolicy();

    @Test
    void 후보보다_group이_많으면_A_B_C_A로_순환한다() {
        List<String> candidates = List.of("A", "B", "C");
        assertThat(policy.select(candidates, 0)).isEqualTo("A");
        assertThat(policy.select(candidates, 1)).isEqualTo("B");
        assertThat(policy.select(candidates, 2)).isEqualTo("C");
        assertThat(policy.select(candidates, 3)).isEqualTo("A");
    }

    @Test
    void 후보가_없으면_배정하지_않는다() {
        assertThatThrownBy(() -> policy.select(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
