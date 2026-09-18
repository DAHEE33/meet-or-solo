package com.survey.meetorsolo.domain.matching.scheduler;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.service.MatchNoShowBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.matching.no-show-scheduler",
        name = "enabled",
        havingValue = "true"
)
public class MatchNoShowScheduler {
    private final MatchNoShowBatchService service;
    private final MatchingSchedulerProperties properties;

    public MatchNoShowScheduler(MatchNoShowBatchService service,
            MatchingSchedulerProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.matching.scheduler.fixed-delay:5s}")
    public void run() {
        service.runBatch(properties.batchSize());
    }
}
