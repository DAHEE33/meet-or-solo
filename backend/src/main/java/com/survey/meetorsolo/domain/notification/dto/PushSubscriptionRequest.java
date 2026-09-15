package com.survey.meetorsolo.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 브라우저 {@code PushSubscription}을 그대로 옮긴 요청.
 *
 * <p>길이 제한은 DB 컬럼과 같은 값이다. 브라우저가 주는 값이라 우리가 형식을 정할 수는 없지만,
 * 저장할 수 없는 길이를 API 경계에서 걸러야 500이 아니라 400으로 답할 수 있다.
 */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 500) String endpoint,
        @NotBlank @Size(max = 255) String p256dh,
        @NotBlank @Size(max = 255) String auth
) {
}
