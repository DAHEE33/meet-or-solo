package com.survey.meetorsolo.global.geo;

import java.math.BigDecimal;

/** mapX(경도)/mapY(위도) 좌표 두 점 사이의 지표면 거리를 haversine 공식으로 계산한다. */
public final class GeoDistanceCalculator {

    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;

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

    /**
     * 중심점과 반경(미터)을 감싸는 위경도 사각 범위를 계산한다. 반경 기준 haversine 원을 완전히
     * 포함하는 정사각형이라 실제 원보다 넓게 잡히지만, 이 범위는 DB에서 후보를 줄이는 사전 필터로만
     * 쓰고 정확한 거리 계산·반경 필터는 그대로 {@link #metersBetween}으로 다시 수행하므로 결과
     * 정확도에는 영향을 주지 않는다(속도 최적화 목적).
     *
     * <p>한국 서비스 범위(위도 약 33~43도)를 전제로 하며, 위도가 극지방에 가까워 경도 방향
     * 미터당 각도가 발작적으로 커지는 경우는 고려하지 않는다.
     */
    public static BoundingBox boundingBox(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        double lat = latitude.doubleValue();
        double lon = longitude.doubleValue();
        double latDelta = radiusMeters / METERS_PER_DEGREE_LATITUDE;
        double metersPerDegreeLongitude = METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(lat));
        double lonDelta = metersPerDegreeLongitude <= 0 ? 180 : radiusMeters / metersPerDegreeLongitude;
        return new BoundingBox(
                BigDecimal.valueOf(lon - lonDelta),
                BigDecimal.valueOf(lon + lonDelta),
                BigDecimal.valueOf(lat - latDelta),
                BigDecimal.valueOf(lat + latDelta)
        );
    }

    public record BoundingBox(
            BigDecimal minLongitude,
            BigDecimal maxLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLatitude
    ) {
    }
}
