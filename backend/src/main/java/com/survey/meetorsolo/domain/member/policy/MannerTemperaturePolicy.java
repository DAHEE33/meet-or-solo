package com.survey.meetorsolo.domain.member.policy;

import java.math.BigDecimal;

/**
 * 매너온도의 허용 범위를 한곳에서 정의한다({@code docs/19} 4.9).
 *
 * <p>지금까지 하한({@code 20.00})만 {@code ReportConfirmationService}에 있었다. 상승 경로가
 * 신고 확정의 반대 방향으로만 존재했기 때문이다. 관리자 수동 조정과 후기 기반 상승이
 * 생기면서 <b>상한이 새로 필요해졌다</b> — 상한이 없으면 온도가 무한히 올라가 지표로서
 * 의미를 잃는다.
 *
 * <p>상한을 {@code 42.00}으로 둔 이유는 체온 은유다. 시작값 {@code 36.50}에서 하한까지
 * {@code -16.50}, 상한까지 {@code +5.50}으로 비대칭이지만 의도한 것이다. 신뢰를 잃는 것은
 * 빠르고 되찾는 것은 느리다는 뜻이며, 상한을 낮게 두면 상위 구간에서 후기가 온도에 아무
 * 영향을 주지 못해 후기를 쓸 이유가 사라진다.
 */
public final class MannerTemperaturePolicy {

    /** 신규 회원의 시작 온도. {@code members.manner_temperature} 기본값과 같아야 한다. */
    public static final BigDecimal INITIAL = new BigDecimal("36.50");

    /** 하한. 이 아래로는 어떤 경로로도 내려가지 않는다. */
    public static final BigDecimal FLOOR = new BigDecimal("20.00");

    /** 상한. 이 위로는 어떤 경로로도 올라가지 않는다. */
    public static final BigDecimal CEILING = new BigDecimal("42.00");

    private MannerTemperaturePolicy() {
    }

    /** 허용 범위 안의 값인지. 경계값은 포함한다. */
    public static boolean isWithinRange(BigDecimal temperature) {
        return temperature != null
                && temperature.compareTo(FLOOR) >= 0
                && temperature.compareTo(CEILING) <= 0;
    }
}
