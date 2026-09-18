package com.survey.meetorsolo.domain.admin.member.dto;

import java.time.Duration;

public enum AdminSuspensionDuration {
    ONE_DAY(1),
    THREE_DAYS(3),
    SEVEN_DAYS(7),
    THIRTY_DAYS(30);

    private final int days;

    AdminSuspensionDuration(int days) {
        this.days = days;
    }

    public Duration duration() {
        return Duration.ofDays(days);
    }
}
