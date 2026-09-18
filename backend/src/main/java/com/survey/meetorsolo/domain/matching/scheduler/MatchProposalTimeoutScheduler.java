package com.survey.meetorsolo.domain.matching.scheduler;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.service.MatchProposalTimeoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.matching.scheduler", name = "enabled", havingValue = "true")
public class MatchProposalTimeoutScheduler {
    private final MatchProposalTimeoutService service;
    private final MatchingSchedulerProperties properties;
    public MatchProposalTimeoutScheduler(MatchProposalTimeoutService service, MatchingSchedulerProperties properties) {
        this.service = service; this.properties = properties;
    }
    @Scheduled(fixedDelayString = "${app.matching.scheduler.fixed-delay:5s}")
    public void run() { service.runBatch(properties.batchSize()); }
}
