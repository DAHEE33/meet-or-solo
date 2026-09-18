package com.survey.meetorsolo.domain.notification.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.notification.dto.NotificationListResponse;
import com.survey.meetorsolo.domain.notification.service.NotificationQueryService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 알림함({@code docs/32} 3.3).
 *
 * <p>본인 것만 읽고 본인 것만 읽음 처리한다. 경로에 회원 id를 두지 않고 token에서 꺼내는
 * 이유가 그것이다 — 남의 알림함을 가리킬 수 있는 입구를 만들지 않는다.
 */
@RestController
@RequestMapping("/api/members/me/notifications")
public class NotificationController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final NotificationQueryService notifications;
    private final JwtProvider jwtProvider;

    public NotificationController(NotificationQueryService notifications, JwtProvider jwtProvider) {
        this.notifications = notifications;
        this.jwtProvider = jwtProvider;
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(notifications.list(memberId(accessToken), size));
    }

    /**
     * 목록을 열면 전부 읽음으로 본다({@code docs/32} 5절 6번).
     *
     * <p>{@code PATCH}인 이유는 반복해도 결과가 같기 때문이다. 읽지 않은 알림이 없으면
     * 아무것도 바뀌지 않고 같은 목록이 돌아온다.
     */
    @PatchMapping("/read")
    public ApiResponse<NotificationListResponse> markAllRead(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(notifications.markAllRead(memberId(accessToken), size));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
