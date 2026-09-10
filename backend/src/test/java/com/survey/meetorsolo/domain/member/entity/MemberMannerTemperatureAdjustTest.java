package com.survey.meetorsolo.domain.member.entity;

import static org.assertj.core.api.Assertions.*;

import com.survey.meetorsolo.domain.member.policy.MannerTemperaturePolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 관리자 매너온도 수동 조정의 경계 검증({@code docs/19} 4.9).
 *
 * <p>매너온도는 신고 확정으로만 내려가는 하강 전용 지표였고 복구 수단이 없었다. 이 조정이
 * 유일한 복구 경로이므로, 범위를 벗어난 값이 조용히 저장되면 지표 전체가 무너진다.
 */
class MemberMannerTemperatureAdjustTest {

    private static final BigDecimal FLOOR = MannerTemperaturePolicy.FLOOR;
    private static final BigDecimal CEILING = MannerTemperaturePolicy.CEILING;

    @Test
    void 목표값으로_바꾸고_변경_전_값을_반환한다() {
        Member member = newMember();
        member.decreaseMannerTemperature(new BigDecimal("5.00"), FLOOR);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("31.50");

        assertThat(member.adjustMannerTemperature(new BigDecimal("36.50"), FLOOR, CEILING))
                .isEqualByComparingTo("31.50");
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("36.50");
    }

    @Test
    void 하한과_상한_경계값은_허용한다() {
        Member floorMember = newMember();
        floorMember.adjustMannerTemperature(FLOOR, FLOOR, CEILING);
        assertThat(floorMember.getMannerTemperature()).isEqualByComparingTo(FLOOR);

        Member ceilingMember = newMember();
        ceilingMember.adjustMannerTemperature(CEILING, FLOOR, CEILING);
        assertThat(ceilingMember.getMannerTemperature()).isEqualByComparingTo(CEILING);
    }

    /**
     * 범위를 벗어난 값은 clamp하지 않고 거절한다.
     *
     * <p>clamp하면 관리자가 입력한 값과 저장된 값이 조용히 달라져, 감사 로그를 읽는 사람이
     * 관리자의 의도를 알 수 없다. {@code decreaseMannerTemperature}가 clamp하는 것과 의도적으로
     * 다르다 — 그쪽은 자동 경로라 거절할 상대가 없다.
     */
    @Test
    void 범위를_벗어난_목표값은_clamp하지_않고_거절한다() {
        Member member = newMember();
        assertThatThrownBy(() -> member.adjustMannerTemperature(
                FLOOR.subtract(new BigDecimal("0.01")), FLOOR, CEILING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.adjustMannerTemperature(
                CEILING.add(new BigDecimal("0.01")), FLOOR, CEILING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("36.50");
    }

    @Test
    void 목표값이_없거나_허용범위가_뒤집혀_있으면_거절한다() {
        Member member = newMember();
        assertThatThrownBy(() -> member.adjustMannerTemperature(null, FLOOR, CEILING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.adjustMannerTemperature(new BigDecimal("30.00"), CEILING, FLOOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("36.50");
    }

    /** 저장 scale은 컬럼 정의({@code NUMERIC(5,2)})와 같은 소수점 2자리를 유지한다. */
    @Test
    void 소수점_자릿수가_달라도_2자리로_맞춰_저장한다() {
        Member member = newMember();
        member.adjustMannerTemperature(new BigDecimal("30"), FLOOR, CEILING);
        assertThat(member.getMannerTemperature().scale()).isEqualTo(2);
        assertThat(member.getMannerTemperature()).isEqualByComparingTo("30.00");
    }

    /** 자동 하강의 하한과 수동 조정의 하한이 갈라지지 않아야 한다. */
    @Test
    void 하한은_자동_하강_경로와_같은_값을_쓴다() {
        Member member = newMember();
        for (int i = 0; i < 10; i++) {
            member.decreaseMannerTemperature(new BigDecimal("5.00"), FLOOR);
        }
        assertThat(member.getMannerTemperature()).isEqualByComparingTo(MannerTemperaturePolicy.FLOOR);
        assertThat(MannerTemperaturePolicy.isWithinRange(member.getMannerTemperature())).isTrue();
    }

    private static Member newMember() {
        return Member.createNaverMember("manner-adjust", "member", null);
    }
}
