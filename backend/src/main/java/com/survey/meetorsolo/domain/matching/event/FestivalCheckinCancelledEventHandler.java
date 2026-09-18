package com.survey.meetorsolo.domain.matching.event;

import com.survey.meetorsolo.domain.festival.event.FestivalCheckinCancelledEvent;
import com.survey.meetorsolo.domain.matching.service.MatchPoolCheckinCancellationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * festival 도메인이 발행하는 {@link FestivalCheckinCancelledEvent}를 구독해, 취소된 축제에
 * 남아있는 이 회원의 WAITING match_pool을 정리한다
 * ({@code docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md} 참고).
 */
@Component
public class FestivalCheckinCancelledEventHandler {

    private static final Logger log = LoggerFactory.getLogger(FestivalCheckinCancelledEventHandler.class);

    private final MatchPoolCheckinCancellationService cancellationService;

    public FestivalCheckinCancelledEventHandler(MatchPoolCheckinCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FestivalCheckinCancelledEvent event) {
        try {
            int cancelledCount = cancellationService.cancelWaitingPool(
                    event.memberId(), event.festivalId(), event.cancelledAt());
            if (cancelledCount > 0) {
                log.info(
                        "체크인 취소에 따라 WAITING match pool을 정리했습니다. memberId={}, festivalId={}",
                        event.memberId(),
                        event.festivalId()
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "체크인 취소에 따른 match pool 정리에 실패했습니다. memberId={}, festivalId={}",
                    event.memberId(),
                    event.festivalId(),
                    exception
            );
        }
    }
}
