package com.survey.meetorsolo.domain.matching.scheduler;

import com.survey.meetorsolo.domain.matching.service.MatchingOrchestrationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.matching.scheduler", name = "enabled", havingValue = "true")
public class MatchingScheduler {
    private final MatchingOrchestrationService orchestrationService;
    public MatchingScheduler(MatchingOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }
    // 조합 주기는 수집 시간보다 짧아야 하므로 timeout·close scheduler와 분리한다.
    @Scheduled(fixedDelayString = "${app.matching.scheduler.matching-fixed-delay:2s}")
    public void run() { orchestrationService.runTick(); }
}
