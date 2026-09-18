package com.survey.meetorsolo.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.notification.dto.NotificationListResponse;
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
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-29T12:00:00+09:00");

    @Mock
    private NotificationRepository notifications;

    private NotificationQueryService service() {
        return new NotificationQueryService(
                notifications, Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void 목록과_읽지_않음_수와_보관_정책을_함께_돌려준다() {
        Notification stored = Notification.of(1L, "MATCH_PROPOSED", null, NOW, NOW);
        ReflectionTestUtils.setField(stored, "id", 7L);
        when(notifications.findByMemberIdOrderByCreatedAtDescIdDesc(anyLong(), any()))
                .thenReturn(List.of(stored));
        when(notifications.countByMemberIdAndReadAtIsNull(1L)).thenReturn(1L);

        NotificationListResponse response = service().list(1L, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).notificationId()).isEqualTo(7L);
        assertThat(response.items().get(0).reason()).isEqualTo("MATCH_PROPOSED");
        assertThat(response.items().get(0).read()).isFalse();
        assertThat(response.unreadCount()).isEqualTo(1);
        assertThat(response.retentionDays()).isEqualTo(30);
        assertThat(response.retentionCount()).isEqualTo(NotificationPolicy.RETENTION_COUNT);
    }

    /** 보관 건수가 상한이므로 그보다 큰 size를 받아도 더 줄 것이 없다. */
    @Test
    void size는_기본값과_상한을_벗어나지_않는다() {
        when(notifications.findByMemberIdOrderByCreatedAtDescIdDesc(anyLong(), any()))
                .thenReturn(List.of());
        ArgumentCaptor<Limit> captor = ArgumentCaptor.forClass(Limit.class);

        service().list(1L, null);
        service().list(1L, 0);
        service().list(1L, 5_000);

        verify(notifications, org.mockito.Mockito.times(3))
                .findByMemberIdOrderByCreatedAtDescIdDesc(anyLong(), captor.capture());
        assertThat(captor.getAllValues()).extracting(Limit::max).containsExactly(
                NotificationPolicy.DEFAULT_PAGE_SIZE,
                NotificationPolicy.DEFAULT_PAGE_SIZE,
                NotificationPolicy.MAX_PAGE_SIZE);
    }

    /** 목록을 열면 전부 읽음으로 본다(docs/32 5절 6번 — 현행 유지). */
    @Test
    void 읽음_처리는_읽지_않은_알림을_모두_읽음으로_바꾼다() {
        when(notifications.findByMemberIdOrderByCreatedAtDescIdDesc(anyLong(), any()))
                .thenReturn(List.of());

        service().markAllRead(1L, null);

        verify(notifications).markAllRead(1L, NOW);
    }
}
