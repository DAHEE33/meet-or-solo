package com.survey.meetorsolo.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.notification.entity.PushSubscription;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.survey.meetorsolo.domain.notification.repository.PushSubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    private static final OffsetDateTime OCCURRED = OffsetDateTime.parse("2026-07-29T12:00:00+09:00");

    @Mock
    private PushSubscriptionRepository subscriptions;

    @Mock
    private WebPushSender sender;

    @Mock
    private PushSubscriptionCleanupService cleanup;

    private PushNotificationService service() {
        return new PushNotificationService(subscriptions, sender, cleanup);
    }

    private static PushSubscription subscription(long memberId, String endpoint) {
        return PushSubscription.of(memberId, endpoint, "p256dh", "auth", OCCURRED);
    }

    /** push는 잠금 화면까지 올라오는 가장 시끄러운 경로라 "지금 손을 써야 하는" 것만 보낸다. */
    @Test
    void 지금_손을_써야_하는_사유만_보낸다() {
        when(sender.enabled()).thenReturn(true);
        when(subscriptions.findAllByMemberId(1L)).thenReturn(List.of(subscription(1L, "https://push/1")));

        service().notifyMembers(List.of(1L), "MATCH_PROPOSED", null, OCCURRED);
        service().notifyMembers(List.of(1L), "MATCH_COMPLETED", null, OCCURRED);
        service().notifyMembers(List.of(1L), "MEMBER_ARRIVED", null, OCCURRED);

        verify(sender).sendAsync(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void 행위자_본인에게는_보내지_않는다() {
        when(sender.enabled()).thenReturn(true);
        when(subscriptions.findAllByMemberId(2L)).thenReturn(List.of(subscription(2L, "https://push/2")));

        service().notifyMembers(List.of(1L, 2L), "MATCH_CONFIRMED", 1L, OCCURRED);

        verify(subscriptions, never()).findAllByMemberId(1L);
        verify(sender).sendAsync(
                org.mockito.ArgumentMatchers.eq("https://push/2"),
                anyString(), anyString(), anyString(), any());
    }

    /** 키가 없는 환경에서는 구독을 조회하지도 않는다. */
    @Test
    void 키가_없으면_아무것도_하지_않는다() {
        when(sender.enabled()).thenReturn(false);

        service().notifyMembers(List.of(1L), "MATCH_PROPOSED", null, OCCURRED);

        verify(subscriptions, never()).findAllByMemberId(anyLong());
        verify(sender, never()).sendAsync(anyString(), anyString(), anyString(), anyString(), any());
    }

    /** service worker가 WebSocket과 같은 매핑을 쓸 수 있도록 같은 모양으로 보낸다. */
    @Test
    void payload는_사유와_발생시각만_담는다() {
        when(sender.enabled()).thenReturn(true);
        when(subscriptions.findAllByMemberId(1L)).thenReturn(List.of(subscription(1L, "https://push/1")));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        service().notifyMembers(List.of(1L), "MATCH_PROPOSED", null, OCCURRED);

        verify(sender).sendAsync(anyString(), anyString(), anyString(), payload.capture(), any());
        assertThat(payload.getValue())
                .contains("\"type\":\"MATCHING_STATE_CHANGED\"")
                .contains("\"reason\":\"MATCH_PROPOSED\"")
                .contains("\"occurredAt\":\"2026-07-29T12:00+09:00\"");
    }

    /** push 서비스가 "없는 구독"이라고 답하면 들고 있을 이유가 없다. */
    @Test
    void 사라진_구독은_정리하도록_넘긴다() {
        when(sender.enabled()).thenReturn(true);
        when(subscriptions.findAllByMemberId(1L)).thenReturn(List.of(subscription(1L, "https://push/1")));
        ArgumentCaptor<Runnable> onGone = ArgumentCaptor.forClass(Runnable.class);

        service().notifyMembers(List.of(1L), "MATCH_PROPOSED", null, OCCURRED);

        verify(sender).sendAsync(anyString(), anyString(), anyString(), anyString(), onGone.capture());
        onGone.getValue().run();
        verify(cleanup).removeGone("https://push/1");
    }
}
