package com.survey.meetorsolo.domain.matching.event;

import com.survey.meetorsolo.domain.matching.service.PoolEntryMatchingOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchingPoolEnteredEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MatchingPoolEnteredEventHandler.class);

    private final PoolEntryMatchingOrchestrationService orchestrationService;

    public MatchingPoolEnteredEventHandler(PoolEntryMatchingOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchingPoolEnteredEvent event) {
        try {
            orchestrationService.run(event.poolId(), event.memberId(), event.festivalId());
        } catch (RuntimeException exception) {
            log.error(
                    "pool entry 매칭 trigger 처리에 실패했습니다. poolId={}, memberId={}, festivalId={}",
                    event.poolId(),
                    event.memberId(),
                    event.festivalId(),
                    exception
            );
        }
    }
}
