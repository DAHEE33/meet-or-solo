package com.survey.meetorsolo.domain.notification.service;

import com.survey.meetorsolo.domain.notification.dto.NotificationListResponse;
import com.survey.meetorsolo.domain.notification.dto.NotificationResponse;
import com.survey.meetorsolo.domain.notification.policy.NotificationPolicy;
import com.survey.meetorsolo.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 본인의 알림함을 읽고 읽음 처리한다({@code docs/32} 3.3).
 *
 * <p><b>cursor pagination을 두지 않는다.</b> 보관 건수가 회원당 100건으로 고정돼 있어 목록이
 * 그보다 길어질 수 없다. 더 볼 것이 없는 목록에 페이지 기법을 붙이면 서버·화면·테스트가
 * 모두 늘어나기만 한다.
 */
@Service
public class NotificationQueryService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationQueryService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(long memberId, Integer size) {
        int limit = normalizeSize(size);
        List<NotificationResponse> items =
                notifications.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, Limit.of(limit))
                        .stream()
                        .map(NotificationResponse::from)
                        .toList();
        return new NotificationListResponse(
                items,
                notifications.countByMemberIdAndReadAtIsNull(memberId),
                (int) NotificationPolicy.RETENTION.toDays(),
                NotificationPolicy.RETENTION_COUNT
        );
    }

    /** 목록을 열면 전부 읽음으로 본다. 요청이 반복돼도 결과가 같다. */
    @Transactional
    public NotificationListResponse markAllRead(long memberId, Integer size) {
        notifications.markAllRead(memberId, OffsetDateTime.now(clock));
        return list(memberId, size);
    }

    private static int normalizeSize(Integer size) {
        if (size == null) return NotificationPolicy.DEFAULT_PAGE_SIZE;
        if (size < 1) return NotificationPolicy.DEFAULT_PAGE_SIZE;
        return Math.min(size, NotificationPolicy.MAX_PAGE_SIZE);
    }
}
