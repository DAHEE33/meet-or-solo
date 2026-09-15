package com.survey.meetorsolo.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.notification.entity.Notification;
import com.survey.meetorsolo.domain.notification.policy.NotificationPolicy;
import com.survey.meetorsolo.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationAppendServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-29T12:00:00+09:00");
    private static final OffsetDateTime OCCURRED = OffsetDateTime.parse("2026-07-29T11:59:50+09:00");

    @Mock
    private NotificationRepository notifications;

    private NotificationAppendService service() {
        return new NotificationAppendService(
                notifications, Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul")));
    }

    /**
     * 중간 상태까지 남기면 나중에 목록을 열었을 때 정작 중요한 "매칭이 확정됐다"가 묻힌다
     * (docs/32 5절 5번 — 저장 안 함으로 확정).
     */
    @Test
    void 중간_상태_사유는_알림함에_남기지_않는다() {
        int saved = service().append(List.of(1L, 2L), "MEMBER_ARRIVED", 1L, OCCURRED);

        assertThat(saved).isZero();
        verify(notifications, never()).save(any());
    }

    @Test
    void 종결_사유는_수신자마다_한_줄씩_남긴다() {
        when(notifications.existsByMemberIdAndReasonAndOccurredAt(anyLong(), anyString(), any()))
                .thenReturn(false);

        int saved = service().append(List.of(2L, 1L), "MATCH_PROPOSED", null, OCCURRED);

        assertThat(saved).isEqualTo(2);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, org.mockito.Mockito.times(2)).save(captor.capture());
        // deadlock을 피하려고 항상 회원 ID 오름차순으로 쓴다.
        assertThat(captor.getAllValues()).extracting(Notification::getMemberId)
                .containsExactly(1L, 2L);
        assertThat(captor.getAllValues()).allSatisfy(notification -> {
            assertThat(notification.getReason()).isEqualTo("MATCH_PROPOSED");
            assertThat(notification.getOccurredAt()).isEqualTo(OCCURRED);
            assertThat(notification.getReadAt()).isNull();
        });
    }

    /** 내가 누른 변화를 내 알림함에 남기지 않는다(docs/31 5절 "알림 자기 반향"). */
    @Test
    void 행위자_본인에게는_남기지_않는다() {
        when(notifications.existsByMemberIdAndReasonAndOccurredAt(eq(2L), anyString(), any()))
                .thenReturn(false);

        int saved = service().append(List.of(1L, 2L), "MATCH_CANCELLED", 1L, OCCURRED);

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo(2L);
        assertThat(captor.getValue().getActorMemberId()).isEqualTo(1L);
    }

    /** 재연결·이벤트 재발행으로 같은 알림이 두 번 올 수 있다. */
    @Test
    void 이미_있는_알림은_다시_쌓지_않는다() {
        when(notifications.existsByMemberIdAndReasonAndOccurredAt(1L, "MATCH_COMPLETED", OCCURRED))
                .thenReturn(true);

        int saved = service().append(List.of(1L), "MATCH_COMPLETED", null, OCCURRED);

        assertThat(saved).isZero();
        verify(notifications, never()).save(any());
    }

    /** 보관 기간과 건수를 둘 다 건다(docs/32 5절 4번). */
    @Test
    void 저장할_때마다_보관_기간과_건수를_정리한다() {
        when(notifications.existsByMemberIdAndReasonAndOccurredAt(anyLong(), anyString(), any()))
                .thenReturn(false);

        service().append(List.of(1L), "MATCH_CONFIRMED", null, OCCURRED);

        verify(notifications).deleteOlderThan(1L, NOW.minus(NotificationPolicy.RETENTION));
        verify(notifications).deleteBeyondNewest(1L, NotificationPolicy.RETENTION_COUNT);
    }
}
