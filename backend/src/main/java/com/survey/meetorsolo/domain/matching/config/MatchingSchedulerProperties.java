package com.survey.meetorsolo.domain.matching.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.matching.scheduler")
public record MatchingSchedulerProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration staleTimeout,
        Duration proposalTimeout,
        int batchSize
) {
    public MatchingSchedulerProperties {
        requirePositive(fixedDelay, "fixed-delay");
        requirePositive(staleTimeout, "stale-timeout");
        requirePositive(proposalTimeout, "proposal-timeout");
        if (batchSize <= 0) throw new IllegalArgumentException("app.matching.scheduler.batch-size는 양수여야 합니다.");
    }
    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("app.matching.scheduler." + name + "는 양수여야 합니다.");
        }
    }
}
