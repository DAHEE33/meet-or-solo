package com.survey.meetorsolo.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SeoulDateTimeTest {

    @Test
    void 현재_시각은_절대_시점을_이동하지_않고_KST_offset으로_생성한다() {
        Instant before = Instant.now();

        OffsetDateTime now = SeoulDateTime.now();

        Instant after = Instant.now();
        assertThat(now.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(now.toInstant()).isBetween(before, after);
        assertThat(Duration.between(before, now.toInstant()).abs()).isLessThan(Duration.ofSeconds(1));
    }
}
