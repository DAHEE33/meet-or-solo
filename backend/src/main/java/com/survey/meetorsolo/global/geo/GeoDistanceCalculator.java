package com.survey.meetorsolo.global.geo;

import java.math.BigDecimal;

/** mapX(경도)/mapY(위도) 좌표 두 점 사이의 지표면 거리를 haversine 공식으로 계산한다. */
public final class GeoDistanceCalculator {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private GeoDistanceCalculator() {
    }

    public static long metersBetween(
            BigDecimal latitude1,
            BigDecimal longitude1,
            BigDecimal latitude2,
            BigDecimal longitude2
    ) {
        double phi1 = Math.toRadians(latitude1.doubleValue());
        double phi2 = Math.toRadians(latitude2.doubleValue());
        double deltaPhi = Math.toRadians(latitude2.subtract(latitude1).doubleValue());
        double deltaLambda = Math.toRadians(longitude2.subtract(longitude1).doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_METERS * c);
    }
}
