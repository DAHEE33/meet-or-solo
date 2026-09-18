package com.survey.meetorsolo.domain.member.entity;

import static org.assertj.core.api.Assertions.*;

import com.survey.meetorsolo.domain.member.policy.MannerTemperaturePolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 매너온도 상승 경로의 경계 검증({@code docs/19} 4.9).
 *
 * <p>상승은 경로마다 상한이 다르다. 만남 완료 보상은 {@code CEILING}(42.00)까지, 시간 경과
 * 회복은 {@code INITIAL}(36.50)까지다. 이 구분이 깨지면 아무 활동도 하지 않은 회원이 가만히
 * 있다가 상한에 도달해 지표가 "가입한 지 얼마나 됐나"를 뜻하게 된다.
 */
class MemberMannerTemperatureRiseTest {

    private static final BigDecimal CEILING = MannerTemperaturePolicy.CEILING;
    private static final BigDecimal RECOVERY_CEILING = MannerTemperaturePolicy.timeRecoveryCeiling();
    private static final BigDecimal FLOOR = MannerTemperaturePolicy.FLOOR;

    @Test
    void 완료_보상은_상한까지_올리고_실제_상승량을_반환한다() {
        Member member = newMember();
        assertThat(member.increaseMannerTemperature(
                MannerTemperaturePolicy.MATCH_COMPLETED_DELTA, CEILING))
                .isEqualByComparingTo("0.50");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("37.00");
    }

    /** 이미 상한이면 0을 돌려준다. 호출자는 이력을 남기지 않는다. */
    @Test
    void 이미_상한이면_0을_반환하고_값이_바뀌지_않는다() {
        Member member = newMember();
        member.adjustMannerTemperature(CEILING, FLOOR, CEILING);

        assertThat(member.increaseMannerTemperature(new BigDecimal("0.50"), CEILING))
                .isEqualByComparingTo("0.00");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo(CEILING);
    }

    @Test
    void 상한을_넘어서는_상승은_상한까지만_적용된다() {
        Member member = newMember();
        member.adjustMannerTemperature(new BigDecimal("41.80"), FLOOR, CEILING);

        // 41.80 + 0.50 = 42.30이지만 상한 42.00으로 잘리므로 실제 상승량은 0.20이다.
        assertThat(member.increaseMannerTemperature(new BigDecimal("0.50"), CEILING))
                .isEqualByComparingTo("0.20");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo(CEILING);
    }

    /**
     * 시간 경과 회복은 시작값을 넘지 못한다. 넘게 하면 활동하지 않은 회원도 상한에 도달한다.
     */
    @Test
    void 시간_경과_회복은_시작값을_넘지_않는다() {
        Member member = newMember();
        member.adjustMannerTemperature(new BigDecimal("36.30"), FLOOR, CEILING);

        assertThat(member.increaseMannerTemperature(
                MannerTemperaturePolicy.TIME_RECOVERY_DELTA, RECOVERY_CEILING))
                .isEqualByComparingTo("0.20");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo(RECOVERY_CEILING);

        // 시작값에 도달한 뒤에는 시간이 아무리 지나도 더 오르지 않는다.
        assertThat(member.increaseMannerTemperature(
                MannerTemperaturePolicy.TIME_RECOVERY_DELTA, RECOVERY_CEILING))
                .isEqualByComparingTo("0.00");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo(RECOVERY_CEILING);
    }

    @Test
    void 상승량이_양수가_아니거나_상한이_없으면_거절한다() {
        Member member = newMember();
        assertThatThrownBy(() -> member.increaseMannerTemperature(BigDecimal.ZERO, CEILING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.increaseMannerTemperature(new BigDecimal("-1.00"), CEILING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.increaseMannerTemperature(new BigDecimal("0.50"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("36.50");
    }

    /**
     * 값 체계 전체가 맞물리는지 확인한다. 신고 확정 차감량을 {@code 2.00}으로 낮춘 이유가
     * "완료 보상으로 되돌릴 수 있어야 한다"이므로, 신고 1건은 완료 4번으로 정확히 회복돼야 한다.
     */
    @Test
    void 신고_1건은_완료_4번으로_정확히_회복된다() {
        Member member = newMember();
        member.decreaseMannerTemperature(MannerTemperaturePolicy.REPORT_CONFIRMED_DELTA, FLOOR);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("34.50");

        for (int i = 0; i < 4; i++) {
            member.increaseMannerTemperature(MannerTemperaturePolicy.MATCH_COMPLETED_DELTA, CEILING);
        }
        assertThat(member.getMannerTemperature()).isEqualByComparingTo(MannerTemperaturePolicy.INITIAL);
    }

    /**
     * 30도 매칭 제한(PR C)이 관리자 안전 알림(신고 3건)보다 먼저 발동하면 안 된다.
     * 차감량을 5.00에서 2.00으로 낮춘 이유가 이것이므로 숫자로 못 박는다.
     */
    @Test
    void 신고_3건까지는_30도_위에_남는다() {
        Member member = newMember();
        for (int i = 0; i < 3; i++) {
            member.decreaseMannerTemperature(MannerTemperaturePolicy.REPORT_CONFIRMED_DELTA, FLOOR);
        }
        assertThat(member.getMannerTemperature()).isGreaterThan(new BigDecimal("30.00"));

        member.decreaseMannerTemperature(MannerTemperaturePolicy.REPORT_CONFIRMED_DELTA, FLOOR);
        assertThat(member.getMannerTemperature()).isLessThan(new BigDecimal("30.00"));
    }

    private static Member newMember() {
        return Member.createNaverMember("manner-rise", "member", null);
    }
}
