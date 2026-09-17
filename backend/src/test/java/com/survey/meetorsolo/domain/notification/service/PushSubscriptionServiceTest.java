package com.survey.meetorsolo.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.notification.config.WebPushProperties;
import com.survey.meetorsolo.domain.notification.dto.PushSubscriptionRequest;
import com.survey.meetorsolo.domain.notification.entity.PushSubscription;
import com.survey.meetorsolo.domain.notification.repository.PushSubscriptionRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-29T12:00:00+09:00");
    private static final PushSubscriptionRequest REQUEST =
            new PushSubscriptionRequest("https://push/1", "p256dh", "auth");

    @Mock
    private PushSubscriptionRepository subscriptions;

    private PushSubscriptionService service(String publicKey, String privateKey) {
        return new PushSubscriptionService(
                subscriptions,
                new WebPushProperties(publicKey, privateKey, ""),
                Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul")));
    }

    private PushSubscriptionService service() {
        return service("public", "private");
    }

    @Test
    void 처음_보는_구독은_새로_저장한다() {
        when(subscriptions.findByEndpoint("https://push/1")).thenReturn(Optional.empty());

        service().subscribe(7L, REQUEST);

        verify(subscriptions).save(any(PushSubscription.class));
    }

    /**
     * 한 기기에서 로그아웃하고 다른 계정으로 들어온 경우다. 새 행을 만들면 예전 주인에게도
     * 계속 알림이 간다.
     */
    @Test
    void 같은_기기가_다시_구독하면_주인을_옮긴다() {
        PushSubscription existing = PushSubscription.of(1L, "https://push/1", "old", "old", NOW);
        when(subscriptions.findByEndpoint("https://push/1")).thenReturn(Optional.of(existing));

        service().subscribe(7L, REQUEST);

        assertThat(existing.getMemberId()).isEqualTo(7L);
        assertThat(existing.getP256dh()).isEqualTo("p256dh");
        verify(subscriptions, never()).save(any(PushSubscription.class));
    }

    @Test
    void 해지는_본인_구독만_지운다() {
        service().unsubscribe(7L, "https://push/1");

        verify(subscriptions).deleteByMemberIdAndEndpoint(7L, "https://push/1");
    }

    /** 키가 없는 환경에서는 빈 문자열을 준다. 화면은 이 값을 보고 push 안내를 띄우지 않는다. */
    @Test
    void 키가_없으면_공개키를_주지_않는다() {
        assertThat(service("", "").publicKey()).isEmpty();
        assertThat(service("public", "").publicKey()).isEmpty();
        assertThat(service().publicKey()).isEqualTo("public");
    }
}
