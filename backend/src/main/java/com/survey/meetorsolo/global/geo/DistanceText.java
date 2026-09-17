package com.survey.meetorsolo.global.geo;

import java.util.Locale;

/**
 * 사용자에게 보여 줄 거리 문구를 만든다.
 *
 * <p>체크인·도착이 반경 밖으로 거절될 때 "얼마나 떨어져 있는지"를 함께 알려 주기 위한 것이다.
 * 거절 사유만 알려 주면 사용자는 GPS 문제인지 자기가 정말 먼 것인지 구분할 수 없다
 * ({@code docs/32} 3.1).
 *
 * <p>미터 단위를 그대로 쓰지 않고 10m 단위로 반올림한다. 좌표 자체는 저장하지 않는다는 원칙과
 * 같은 맥락이다 — 판단에 필요한 만큼만 보여 주면 된다.
 */
public final class DistanceText {

    private DistanceText() {
    }

    public static String of(long meters) {
        if (meters < 1000) {
            return (Math.round(meters / 10.0) * 10) + "m";
        }
        return String.format(Locale.ROOT, "%.1fkm", meters / 1000.0);
    }
}
