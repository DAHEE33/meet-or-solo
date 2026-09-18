package com.survey.meetorsolo.domain.inquiry.scheduler;

import com.survey.meetorsolo.domain.inquiry.service.InquiryRetentionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간이 지난 문의를 주기적으로 익명화한다.
 *
 * <p>{@code MemberSuspensionExpiryScheduler}와 같은 형태다 — 기본 비활성이고 dev/prod에서만
 * 켠다. 여러 instance가 동시에 돌면 같은 대상을 중복 처리할 수 있지만, 익명화는 멱등하고
 * {@code anonymized_at}이 재처리를 막으므로 결과가 달라지지 않는다.
 */
@Component
@ConditionalOnProperty(
        name = "app.inquiry.retention-scheduler-enabled",
        havingValue = "true"
)
public class InquiryRetentionScheduler {

    private final InquiryRetentionService retentionService;

    public InquiryRetentionScheduler(InquiryRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(fixedDelayString = "${app.inquiry.retention-scheduler-fixed-delay:3600000}")
    public void anonymizeExpired() {
        retentionService.anonymizeBatch(100);
    }
}
