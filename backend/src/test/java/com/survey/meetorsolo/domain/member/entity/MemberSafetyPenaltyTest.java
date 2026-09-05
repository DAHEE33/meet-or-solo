package com.survey.meetorsolo.domain.member.entity;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 관리자 유효 판정 신고가 사용하는 penalty score 누적과 매너온도 하한 clamp 검증.
 * 정책은 docs/05_MATCHING_POLICY.md의 "관리자 유효 판정 신고 (REPORT_CONFIRMED)" 절이다.
 */
class MemberSafetyPenaltyTest {

    private static final BigDecimal DELTA = new BigDecimal("5.00");
    private static final BigDecimal FLOOR = new BigDecimal("20.00");

    @Test
    void 기본_매너온도_36_50에서_5도씩_차감된다() {
        Member member = newMember();
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("36.50");

        assertThat(member.decreaseMannerTemperature(DELTA, FLOOR)).isEqualByComparingTo("5.00");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("31.50");

        member.decreaseMannerTemperature(DELTA, FLOOR);
        member.decreaseMannerTemperature(DELTA, FLOOR);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("21.50");
    }

    @Test
    void 하한을_넘어서는_차감은_하한까지만_적용되고_실제_차감량을_반환한다() {
        Member member = newMember();
        for (int i = 0; i < 3; i++) {
            member.decreaseMannerTemperature(DELTA, FLOOR);
        }
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("21.50");

        // 21.50 - 5.00 = 16.50이지만 하한 20.00으로 clamp되므로 실제 차감량은 1.50이다.
        assertThat(member.decreaseMannerTemperature(DELTA, FLOOR)).isEqualByComparingTo("1.50");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("20.00");
    }

    @Test
    void 이미_하한이면_차감량은_0이고_온도가_변하지_않는다() {
        Member member = newMember();
        for (int i = 0; i < 4; i++) {
            member.decreaseMannerTemperature(DELTA, FLOOR);
        }
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("20.00");

        assertThat(member.decreaseMannerTemperature(DELTA, FLOOR)).isEqualByComparingTo("0");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("20.00");
    }

    @Test
    void 매너온도는_0_100_CHECK_범위를_벗어나지_않는다() {
        Member member = newMember();
        for (int i = 0; i < 50; i++) {
            member.decreaseMannerTemperature(DELTA, FLOOR);
        }
        assertThat(member.getMannerTemperature()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("20.00");
    }

    @Test
    void 차감량과_하한이_잘못되면_거절한다() {
        Member member = newMember();
        assertThatThrownBy(() -> member.decreaseMannerTemperature(null, FLOOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.decreaseMannerTemperature(BigDecimal.ZERO, FLOOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.decreaseMannerTemperature(new BigDecimal("-1"), FLOOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.decreaseMannerTemperature(DELTA, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("36.50");
    }

    @Test
    void penalty_score는_상한_없이_누적된다() {
        Member member = newMember();
        assertThat(member.getPenaltyScore()).isZero();

        member.increasePenaltyScore(5);
        member.increasePenaltyScore(5);
        assertThat(member.getPenaltyScore()).isEqualTo(10);
    }

    @Test
    void penalty_score_증가량이_양수가_아니면_거절한다() {
        Member member = newMember();
        assertThatThrownBy(() -> member.increasePenaltyScore(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.increasePenaltyScore(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getPenaltyScore()).isZero();
    }

    private static Member newMember() {
        return Member.createNaverMember("safety-penalty", "member", null);
    }
}
