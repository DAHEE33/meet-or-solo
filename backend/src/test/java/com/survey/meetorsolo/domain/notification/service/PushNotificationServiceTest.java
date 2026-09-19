package com.survey.meetorsolo.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.notification.entity.PushSubscription;
import com.survey.meetorsolo.domain.notification.policy.NotificationPolicy;
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

    /**
     * 확정을 만든 본인에게도 보낸다.
     *
     * <p>{@code MATCH_CONFIRMED}의 행위자는 마지막으로 수락한 사람이다. 만남 장소로 이동을
     * 시작해야 하고 도착 마감이 30분이라, 그 사람이야말로 잠금 화면에서 봐야 한다. 행위자를
     * 무조건 걸러내던 때는 매칭을 성사시킨 본인만 push를 못 받았다.
     */
    @Test
    void 그룹_사실은_행위자에게도_보낸다() {
        when(sender.enabled()).thenReturn(true);
        when(subscriptions.findAllByMemberId(1L)).thenReturn(List.of(subscription(1L, "https://push/1")));
        when(subscriptions.findAllByMemberId(2L)).thenReturn(List.of(subscription(2L, "https://push/2")));

        service().notifyMembers(List.of(1L, 2L), "MATCH_CONFIRMED", 1L, OCCURRED);

        verify(sender).sendAsync(
                org.mockito.ArgumentMatchers.eq("https://push/1"),
                anyString(), anyString(), anyString(), any());
        verify(sender).sendAsync(
                org.mockito.ArgumentMatchers.eq("https://push/2"),
                anyString(), anyString(), anyString(), any());
    }

    /**
     * 관측자 시점 사유는 행위자에게 보내지 않는다(docs/31 5절 "알림 자기 반향").
     *
     * <p>push 사유({@code MATCH_PROPOSED}·{@code MATCH_CONFIRMED})에는 관측자 시점이 없어
     * 지금은 여기서 걸러질 일이 없다. 그래도 WebSocket·알림함과 <b>같은 판정</b>을 쓰는지
     * 고정해 둔다 — 세 경로 중 하나만 규칙이 달라지는 것이 이번 결함의 원인이었다.
     */
    @Test
    void 관측자_시점_사유는_행위자에게_보내지_않는다() {
        when(sender.enabled()).thenReturn(true);

        service().notifyMembers(List.of(1L, 2L), "MEMBER_ARRIVED", 1L, OCCURRED);

        // 애초에 push 대상 사유가 아니라 구독 조회조차 하지 않는다.
        verify(subscriptions, never()).findAllByMemberId(anyLong());
        assertThat(NotificationPolicy.deliverableTo("MEMBER_ARRIVED", 1L, 1L)).isFalse();
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
