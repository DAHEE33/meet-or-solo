package com.survey.meetorsolo.domain.notification.service;

import com.survey.meetorsolo.domain.notification.config.WebPushProperties;
import com.survey.meetorsolo.domain.notification.dto.PushSubscriptionRequest;
import com.survey.meetorsolo.domain.notification.entity.PushSubscription;
import com.survey.meetorsolo.domain.notification.repository.PushSubscriptionRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 브라우저 push 구독을 등록·해지한다({@code docs/32} 3.4).
 */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository subscriptions;
    private final WebPushProperties properties;
    private final Clock clock;

    public PushSubscriptionService(
            PushSubscriptionRepository subscriptions, WebPushProperties properties, Clock clock) {
        this.subscriptions = subscriptions;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 구독을 등록하거나 주인을 바꾼다.
     *
     * <p>같은 endpoint가 이미 있으면 새로 만들지 않고 주인과 키를 옮긴다. 한 기기에서
     * 로그아웃하고 다른 계정으로 들어온 경우가 그렇다 — 예전 주인에게 계속 알림이 가면 안 된다.
     */
    @Transactional
    public void subscribe(long memberId, PushSubscriptionRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        subscriptions.findByEndpoint(request.endpoint())
                .ifPresentOrElse(
                        existing -> existing.reassign(memberId, request.p256dh(), request.auth(), now),
                        () -> subscriptions.save(PushSubscription.of(
                                memberId, request.endpoint(), request.p256dh(), request.auth(), now)));
    }

    /** 본인 구독만 지운다. 남의 endpoint를 보내도 아무 일도 일어나지 않는다. */
    @Transactional
    public void unsubscribe(long memberId, String endpoint) {
        subscriptions.deleteByMemberIdAndEndpoint(memberId, endpoint);
    }

    /**
     * 브라우저가 구독할 때 쓰는 VAPID 공개키.
     *
     * <p>공개키는 숨길 값이 아니다 — 브라우저에 그대로 들어간다. 키가 없으면 빈 문자열을
     * 돌려주고, 화면은 그걸 보고 push 기능을 아예 띄우지 않는다.
     */
    public String publicKey() {
        return properties.enabled() ? properties.publicKey() : "";
    }
}
