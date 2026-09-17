package com.survey.meetorsolo.domain.notification.dto;

/**
 * 브라우저 구독에 필요한 VAPID 공개키.
 *
 * <p>{@code publicKey}가 빈 문자열이면 이 환경에는 push가 설정되지 않았다는 뜻이다.
 */
public record VapidPublicKeyResponse(String publicKey) {
}
