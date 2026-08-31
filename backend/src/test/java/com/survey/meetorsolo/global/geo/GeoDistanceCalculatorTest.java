package com.survey.meetorsolo.global.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GeoDistanceCalculatorTest {

    @Test
    void 같은_지점의_거리는_0이다() {
        BigDecimal lat = new BigDecimal("37.1000000000");
        BigDecimal lon = new BigDecimal("128.1000000000");
        assertThat(GeoDistanceCalculator.metersBetween(lat, lon, lat, lon)).isZero();
    }

    @Test
    void bounding_box는_중심점_기준_위도_경도_범위를_반환한다() {
        BigDecimal latitude = new BigDecimal("37.0000000000");
        BigDecimal longitude = new BigDecimal("128.0000000000");

        var box = GeoDistanceCalculator.boundingBox(latitude, longitude, 1_000);

        assertThat(box.minLatitude().doubleValue()).isLessThan(37.0);
        assertThat(box.maxLatitude().doubleValue()).isGreaterThan(37.0);
        assertThat(box.minLongitude().doubleValue()).isLessThan(128.0);
        assertThat(box.maxLongitude().doubleValue()).isGreaterThan(128.0);
        // 위도 1도 ≈ 111.32km이므로 1,000m 반경은 위도로 약 0.008979도.
        assertThat(37.0 - box.minLatitude().doubleValue()).isCloseTo(0.008979, within(0.0005));
        assertThat(box.maxLatitude().doubleValue() - 37.0).isCloseTo(0.008979, within(0.0005));
    }

    @Test
    void bounding_box는_실제_반경_원을_완전히_포함한다() {
        // 반경 방향으로 정확히 radiusMeters만큼 떨어진 네 점(동/서/남/북)이 모두 box 안에 있어야
        // bounding box 사전 필터가 유효 후보를 누락시키지 않는다.
        BigDecimal latitude = new BigDecimal("37.0000000000");
        BigDecimal longitude = new BigDecimal("128.0000000000");
        int radiusMeters = 2_000;

        var box = GeoDistanceCalculator.boundingBox(latitude, longitude, radiusMeters);

        BigDecimal north = latitude.add(BigDecimal.valueOf(radiusMeters / 111_320.0));
        BigDecimal south = latitude.subtract(BigDecimal.valueOf(radiusMeters / 111_320.0));
        assertThat(north.doubleValue()).isLessThanOrEqualTo(box.maxLatitude().doubleValue());
        assertThat(south.doubleValue()).isGreaterThanOrEqualTo(box.minLatitude().doubleValue());
        assertThat(GeoDistanceCalculator.metersBetween(latitude, longitude, north, longitude))
                .isCloseTo(radiusMeters, org.assertj.core.data.Offset.offset(5L));
    }

    @Test
    void 위도가_높을수록_경도_방향_범위가_넓어진다() {
        BigDecimal longitude = new BigDecimal("128.0000000000");
        var lowLatitudeBox = GeoDistanceCalculator.boundingBox(new BigDecimal("10.0000000000"), longitude, 1_000);
        var highLatitudeBox = GeoDistanceCalculator.boundingBox(new BigDecimal("60.0000000000"), longitude, 1_000);

        double lowLonSpan = lowLatitudeBox.maxLongitude().doubleValue() - lowLatitudeBox.minLongitude().doubleValue();
        double highLonSpan = highLatitudeBox.maxLongitude().doubleValue() - highLatitudeBox.minLongitude().doubleValue();
        assertThat(highLonSpan).isGreaterThan(lowLonSpan);
    }
}
