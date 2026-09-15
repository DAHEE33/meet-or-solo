package com.survey.meetorsolo.domain.notification.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.notification.dto.PushSubscriptionRequest;
import com.survey.meetorsolo.domain.notification.dto.VapidPublicKeyResponse;
import com.survey.meetorsolo.domain.notification.service.PushSubscriptionService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web Push 구독 등록·해지({@code docs/32} 3.4).
 *
 * <p>본인 구독만 다룬다. 경로에 회원 id를 두지 않고 token에서 꺼내는 이유가 그것이다.
 */
@RestController
@RequestMapping("/api/members/me/push-subscriptions")
public class PushSubscriptionController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final PushSubscriptionService subscriptions;
    private final JwtProvider jwtProvider;

    public PushSubscriptionController(
            PushSubscriptionService subscriptions, JwtProvider jwtProvider) {
        this.subscriptions = subscriptions;
        this.jwtProvider = jwtProvider;
    }

    /**
     * 브라우저가 구독할 때 필요한 VAPID 공개키.
     *
     * <p>공개키는 어차피 브라우저에 들어가는 값이라 숨기지 않는다. 키가 설정되지 않은 환경에서는
     * 빈 문자열이 오고, 화면은 그걸 보고 push 안내를 띄우지 않는다.
     */
    @GetMapping("/public-key")
    public ApiResponse<VapidPublicKeyResponse> publicKey(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        memberId(accessToken);
        return ApiResponse.success(new VapidPublicKeyResponse(subscriptions.publicKey()));
    }

    @PostMapping
    public ResponseEntity<Void> subscribe(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        subscriptions.subscribe(memberId(accessToken), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 구독 해지.
     *
     * <p>본문 대신 {@code endpoint}를 받는 이유는 브라우저가 구독을 그 값으로 식별하기
     * 때문이다. 우리 id를 프론트가 들고 있을 이유가 없다.
     */
    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        subscriptions.unsubscribe(memberId(accessToken), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
