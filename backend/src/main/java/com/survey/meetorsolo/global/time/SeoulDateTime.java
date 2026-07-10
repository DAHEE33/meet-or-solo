package com.survey.meetorsolo.global.time;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class SeoulDateTime {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    private SeoulDateTime() {
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZONE_ID);
    }
}
