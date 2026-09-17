package com.survey.meetorsolo.domain.notification.dto;

import java.util.List;

/**
 * 알림 목록과 읽지 않음 수.
 *
 * <p>보관 정책을 함께 내보낸다. 화면이 "최근 100건, 30일까지 보관해요"를 직접 적어 두지 않고
 * 서버 값으로 쓰게 하려는 것이다. 1단계에서 "최근 알림만 이 기기에 보관해요"를 화면에 박아
 * 두었더니, 보관 방식이 바뀐 뒤에도 문구가 그대로 남았다.
 */
public record NotificationListResponse(
        List<NotificationResponse> items,
        long unreadCount,
        int retentionDays,
        int retentionCount
) {
}
