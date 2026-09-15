package com.survey.meetorsolo.domain.notification.dto;

import com.survey.meetorsolo.domain.notification.entity.Notification;
import java.time.OffsetDateTime;

/**
 * 알림 한 줄.
 *
 * <p>문구를 담지 않는다. 사유만 주고 문구·이동 경로는 프론트가 정한다(WebSocket 1단계와 동일).
 *
 * <p><b>행위자 id를 내보내지 않는다.</b> 자기 반향은 저장 시점에 걸러지므로 화면이 알 필요가
 * 없고, 회원 id는 굳이 늘려 노출할 값이 아니다.
 */
public record NotificationResponse(
        long notificationId,
        String reason,
        OffsetDateTime occurredAt,
        boolean read
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getReason(),
                notification.getOccurredAt(),
                notification.getReadAt() != null
        );
    }
}
