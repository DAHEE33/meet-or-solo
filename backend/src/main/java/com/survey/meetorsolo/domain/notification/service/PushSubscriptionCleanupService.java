package com.survey.meetorsolo.domain.notification.service;

import com.survey.meetorsolo.domain.notification.repository.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * push 서비스가 "이 구독은 없다"(404·410)고 답한 구독을 지운다.
 *
 * <p>별도 서비스로 둔 이유는 transaction 때문이다. 삭제는 발송 thread에서 일어나므로 호출자의
 * transaction에 얹을 수 없고, 같은 클래스 안에서 부르면 프록시를 타지 않아
 * {@code @Transactional}이 걸리지 않는다.
 */
@Service
public class PushSubscriptionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionCleanupService.class);

    private final PushSubscriptionRepository subscriptions;

    public PushSubscriptionCleanupService(PushSubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeGone(String endpoint) {
        try {
            subscriptions.deleteByEndpoint(endpoint);
        } catch (RuntimeException failure) {
            // 정리는 부가 작업이다. 다음 발송에서 다시 404가 오면 그때 지워진다.
            log.warn("만료된 push 구독을 지우지 못했습니다.", failure);
        }
    }
}
