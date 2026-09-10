package com.survey.meetorsolo.domain.member.scheduler;

import com.survey.meetorsolo.domain.member.service.MannerTemperatureRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시간 경과 매너온도 회복 스케줄러({@code docs/19} 4.9).
 *
 * <p>기본 주기를 1시간으로 둔다. 회복 간격 자체가 30일이라 초 단위 정확도가 필요 없고,
 * 회원 수만큼 도는 조회라 자주 돌릴 이유가 없다. 한 번에 처리하는 인원은 제한한다 — 오래
 * 방치된 회원부터 처리하므로 batch가 밀려도 순서가 뒤집히지 않는다.
 */
@Component
@ConditionalOnProperty(
        name = "app.member.manner-temperature-recovery-scheduler-enabled",
        havingValue = "true"
)
public class MannerTemperatureRecoveryScheduler {

    private static final int BATCH_SIZE = 200;

    private final MannerTemperatureRecoveryService recoveryService;

    public MannerTemperatureRecoveryScheduler(MannerTemperatureRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelayString =
            "${app.member.manner-temperature-recovery-scheduler-fixed-delay:3600000}")
    public void recover() {
        recoveryService.recoverBatch(BATCH_SIZE);
    }
}
