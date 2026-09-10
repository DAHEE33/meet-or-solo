package com.survey.meetorsolo.domain.member.policy;

import java.math.BigDecimal;

/**
 * 매너온도의 허용 범위와 변동 폭을 한곳에서 정의한다({@code docs/19} 4.9).
 *
 * <p>원래 하한({@code 20.00})만 {@code ReportConfirmationService}에 있었다. 상승 경로가 없어
 * 신고 확정의 반대 방향이 존재하지 않았기 때문이다. 관리자 수동 조정과 상승 경로가 생기면서
 * 범위와 변동 폭을 모두 여기로 모았다.
 *
 * <p>상한을 {@code 42.00}으로 둔 이유는 체온 은유다. 시작값 {@code 36.50}에서 하한까지
 * {@code -16.50}, 상한까지 {@code +5.50}으로 비대칭이지만 의도한 것이다. 신뢰를 잃는 것은
 * 빠르고 되찾는 것은 느리다.
 */
public final class MannerTemperaturePolicy {

    /** 신규 회원의 시작 온도. {@code members.manner_temperature} 기본값과 같아야 한다. */
    public static final BigDecimal INITIAL = new BigDecimal("36.50");

    /** 하한. 이 아래로는 어떤 경로로도 내려가지 않는다. */
    public static final BigDecimal FLOOR = new BigDecimal("20.00");

    /** 상한. 이 위로는 어떤 경로로도 올라가지 않는다. */
    public static final BigDecimal CEILING = new BigDecimal("42.00");

    /**
     * 관리자 유효 판정 신고 1건당 차감량.
     *
     * <p><b>{@code 5.00}에서 낮췄다.</b> 예전 값으로는 신고 확정 2건에 {@code 26.50}이 되어
     * 30도 아래로 떨어졌는데, 관리자 안전 알림 임계는 3건이다. 즉 30도 매칭 제한을 도입하면
     * <b>관리자 알림보다 자동 제한이 먼저 발동</b>해 {@code docs/19} 4.3에서 확정한 "자동
     * 제한은 회원 status를 바꾸지 않고 관리자 알림까지만"을 우회한다.
     *
     * <p>{@code 2.00}이면 30도 아래가 되려면 신고 4건({@code 36.5 - 8 = 28.5})이 필요해
     * 알림(3건)이 먼저 뜨고 그다음 제한이 걸린다. 만남 완료 보상으로 되돌리기에도
     * {@code 5.00}은 너무 컸다 — 완료 10번을 요구하면 회복 경로가 있는 척만 하는 셈이다.
     */
    public static final BigDecimal REPORT_CONFIRMED_DELTA = new BigDecimal("2.00");

    /**
     * 만남을 끝까지 마쳤을 때의 상승량.
     *
     * <p>후기와 무관하게 <b>참여만으로</b> 오른다. 후기는 상대가 안 써주면 잘 참여한 회원도
     * 못 오르는데, 완료는 본인 행동만으로 결정된다. 조작도 어렵다 — 축제 현장 GPS 체크인,
     * 매칭 성사, 만남 장소 전원 도착이 모두 필요하고 완료 후 1시간 재매칭 잠금이 걸린다.
     *
     * <p>{@code 1.00}이 아니라 {@code 0.50}인 이유는 상한 도달 속도다. {@code 1.00}이면 완료
     * 6번에 {@code 42.00}을 찍어 열심히 참여한 회원이 전부 상한에 몰린다. 관리자가 지표를
     * 봐도 구분이 안 된다.
     */
    public static final BigDecimal MATCH_COMPLETED_DELTA = new BigDecimal("0.50");

    /** 시간 경과 회복 1회당 상승량. */
    public static final BigDecimal TIME_RECOVERY_DELTA = new BigDecimal("0.50");

    /**
     * 시간 경과 회복 주기(일).
     *
     * <p>{@code ReportConfirmationService.AGGREGATION_WINDOW_DAYS}(누적 유효 신고 30일 window)와
     * 같은 값이다. 신고 카운트는 30일이 지나면 집계에서 빠지는데 온도만 영구 하강으로 남는
     * 비대칭을 없앤다({@code docs/19} 4.9에서 정리 대상으로 지목한 항목).
     */
    public static final int TIME_RECOVERY_INTERVAL_DAYS = 30;

    private MannerTemperaturePolicy() {
    }

    /** 허용 범위 안의 값인지. 경계값은 포함한다. */
    public static boolean isWithinRange(BigDecimal temperature) {
        return temperature != null
                && temperature.compareTo(FLOOR) >= 0
                && temperature.compareTo(CEILING) <= 0;
    }

    /**
     * 시간 경과 회복의 상한.
     *
     * <p><b>활동 기반 상승과 상한이 다르다.</b> 시간 경과 회복은 {@link #INITIAL}까지만 올린다.
     * {@link #CEILING}까지 올리면 아무 활동도 하지 않은 회원이 가만히 있다가 상한에 도달해
     * 지표가 "가입한 지 얼마나 됐나"를 뜻하게 된다. 시작값을 넘는 구간은 실제로 만남을
     * 마쳐야 오른다.
     */
    public static BigDecimal timeRecoveryCeiling() {
        return INITIAL;
    }
}
