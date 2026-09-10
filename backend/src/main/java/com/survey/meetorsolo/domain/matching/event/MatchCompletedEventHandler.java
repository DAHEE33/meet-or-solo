package com.survey.meetorsolo.domain.matching.event;

import com.survey.meetorsolo.domain.member.service.MannerTemperatureRewardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 만남 완료 후 매너온도 보상을 지급한다({@code docs/19} 4.9).
 *
 * <p>AFTER_COMMIT이다. 보상 실패가 완료 자체를 되돌리면 안 된다 — 사용자는 실제로 만났고
 * 도착까지 마쳤다. 예외를 삼키고 로그만 남기는 이유가 그것이다. 놓친 보상은
 * {@code manner_temperature_events}에 행이 없는 것으로 드러나므로 나중에 보정할 수 있다.
 */
@Component
public class MatchCompletedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MatchCompletedEventHandler.class);

    private final MannerTemperatureRewardService rewards;

    public MatchCompletedEventHandler(MannerTemperatureRewardService rewards) {
        this.rewards = rewards;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchCompletedEvent event) {
        try {
            int rewarded = rewards.rewardCompletedMembers(
                    event.groupId(), event.memberIds(), event.completedAt());
            if (rewarded > 0) {
                log.info("만남 완료 매너온도 보상을 지급했습니다. groupId={}, memberCount={}",
                        event.groupId(), rewarded);
            }
        } catch (RuntimeException exception) {
            log.error("만남 완료 매너온도 보상에 실패했습니다. groupId={}, memberIds={}",
                    event.groupId(), event.memberIds(), exception);
        }
    }
}
