package com.survey.meetorsolo.domain.admin.member.scheduler;

import com.survey.meetorsolo.domain.admin.member.service.MemberSuspensionExpiryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.admin.member.suspension-scheduler-enabled",
        havingValue = "true"
)
public class MemberSuspensionExpiryScheduler {

    private final MemberSuspensionExpiryService expiryService;

    public MemberSuspensionExpiryScheduler(MemberSuspensionExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(fixedDelayString = "${app.admin.member.suspension-scheduler-fixed-delay:60000}")
    public void restoreExpired() {
        expiryService.restoreBatch(100);
    }
}
