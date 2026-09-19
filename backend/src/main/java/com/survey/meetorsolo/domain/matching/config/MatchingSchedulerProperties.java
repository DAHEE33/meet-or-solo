package com.survey.meetorsolo.domain.matching.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.matching.scheduler")
public record MatchingSchedulerProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration matchingFixedDelay,
        Duration collectWindow,
        Duration staleTimeout,
        Duration proposalTimeout,
        int batchSize
) {
    public MatchingSchedulerProperties {
        requirePositive(fixedDelay, "fixed-delay");
        requirePositive(matchingFixedDelay, "matching-fixed-delay");
        requirePositive(collectWindow, "collect-window");
        // 조합 tick이 수집 시간보다 촘촘해야 수집이 끝나자마자 평가된다. 같거나 길면 수집 종료 후
        // 최대 한 주기를 더 기다리게 되어 수집 구간의 의미가 희석된다.
        if (matchingFixedDelay.compareTo(collectWindow) >= 0) {
            throw new IllegalArgumentException(
                    "app.matching.scheduler.matching-fixed-delay는 collect-window보다 짧아야 합니다: "
                            + matchingFixedDelay + " >= " + collectWindow);
        }
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
