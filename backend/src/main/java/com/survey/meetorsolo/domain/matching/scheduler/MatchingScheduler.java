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
    @Scheduled(fixedDelayString = "${app.matching.scheduler.fixed-delay:5s}")
    public void run() { orchestrationService.runTick(); }
}
