package com.survey.meetorsolo.domain.member.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 30도 매칭 제한의 경계({@code docs/19} 4.9 PR C).
 *
 * <p>이 값과 차감량은 함께 움직인다. 차감량이 {@code 5.00}으로 돌아가면 신고 2건에 제한이
 * 걸려 관리자 안전 알림(3건)보다 자동 제한이 <b>먼저</b> 발동한다. 그 순서를 여기서 고정한다.
 */
class MannerTemperatureMatchingLimitTest {

    @Test
    void 경계값_30도는_매칭할_수_있고_그_아래는_막힌다() {
        assertThat(MannerTemperaturePolicy.matchingAllowed(new BigDecimal("30.00"))).isTrue();
        assertThat(MannerTemperaturePolicy.matchingAllowed(new BigDecimal("29.99"))).isFalse();
        assertThat(MannerTemperaturePolicy.matchingAllowed(MannerTemperaturePolicy.FLOOR)).isFalse();
        assertThat(MannerTemperaturePolicy.matchingAllowed(MannerTemperaturePolicy.INITIAL)).isTrue();
    }

    /** 온도를 읽지 못한 경우까지 막으면, 조회 실패가 곧 매칭 금지가 된다. */
    @Test
    void 온도를_모르면_막지_않는다() {
        assertThat(MannerTemperaturePolicy.matchingAllowed(null)).isTrue();
    }

    /** 관리자 안전 알림(3건)이 자동 제한보다 먼저 떠야 한다. */
    @Test
    void 신고_확정_3건까지는_제한에_걸리지_않고_4건에서_걸린다() {
        BigDecimal afterThree = MannerTemperaturePolicy.INITIAL
                .subtract(MannerTemperaturePolicy.REPORT_CONFIRMED_DELTA.multiply(new BigDecimal("3")));
        BigDecimal afterFour = MannerTemperaturePolicy.INITIAL
                .subtract(MannerTemperaturePolicy.REPORT_CONFIRMED_DELTA.multiply(new BigDecimal("4")));

        assertThat(afterThree).isEqualByComparingTo("30.50");
        assertThat(MannerTemperaturePolicy.matchingAllowed(afterThree)).isTrue();
        assertThat(afterFour).isEqualByComparingTo("28.50");
        assertThat(MannerTemperaturePolicy.matchingAllowed(afterFour)).isFalse();
    }

    /**
     * 회복 경로가 살아 있어야 한다. 없으면 "신고 4건 → 매칭 금지 → 만남 불가 → 회복 불가"
     * 데드락이 된다. 시간 경과 회복은 매칭 없이도 오르므로 그 경로로 제한이 풀린다.
     */
    @Test
    void 시간_경과_회복만으로도_제한_아래에서_벗어난다() {
        BigDecimal afterFour = new BigDecimal("28.50");
        BigDecimal recoveredThreeTimes = afterFour.add(
                MannerTemperaturePolicy.TIME_RECOVERY_DELTA.multiply(new BigDecimal("3")));

        assertThat(recoveredThreeTimes).isEqualByComparingTo("30.00");
        assertThat(MannerTemperaturePolicy.matchingAllowed(recoveredThreeTimes)).isTrue();
    }
}
