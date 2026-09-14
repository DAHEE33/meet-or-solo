package com.survey.meetorsolo.domain.matching.scheduler;

import com.survey.meetorsolo.domain.matching.config.MatchingSchedulerProperties;
import com.survey.meetorsolo.domain.matching.service.MatchMeetingCloseBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만남 시간이 끝난 그룹을 닫는 주기 작업이다.
 *
 * <p>노쇼 배치와 달리 {@code app.matching.scheduler.enabled}에 건다. 노쇼 처리는 페널티라
 * 환경에 따라 끌 수 있지만, 그룹을 닫는 것은 매칭 수명주기의 일부여서 꺼지면 참가자가 새 매칭을
 * 신청하지 못한 채 남는다({@code MatchMeetingCloseGroupService} 주석 참고).
 */
@Component
@ConditionalOnProperty(prefix = "app.matching.scheduler", name = "enabled", havingValue = "true")
public class MatchMeetingCloseScheduler {

    private final MatchMeetingCloseBatchService service;
    private final MatchingSchedulerProperties properties;

    public MatchMeetingCloseScheduler(MatchMeetingCloseBatchService service,
            MatchingSchedulerProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.matching.scheduler.fixed-delay:5s}")
    public void run() {
        service.runBatch(properties.batchSize());
    }
}
