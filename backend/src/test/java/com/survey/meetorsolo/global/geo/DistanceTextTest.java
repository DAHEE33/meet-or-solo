package com.survey.meetorsolo.global.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DistanceTextTest {

    @Test
    void 천미터_미만은_십미터_단위로_반올림한다() {
        assertThat(DistanceText.of(512)).isEqualTo("510m");
        assertThat(DistanceText.of(515)).isEqualTo("520m");
        assertThat(DistanceText.of(150)).isEqualTo("150m");
    }

    @Test
    void 천미터_이상은_킬로미터로_바꾼다() {
        assertThat(DistanceText.of(1000)).isEqualTo("1.0km");
        assertThat(DistanceText.of(1240)).isEqualTo("1.2km");
        assertThat(DistanceText.of(70123)).isEqualTo("70.1km");
    }
}
