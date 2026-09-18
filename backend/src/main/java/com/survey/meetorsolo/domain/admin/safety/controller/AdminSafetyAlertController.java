package com.survey.meetorsolo.domain.admin.safety.controller;

import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertPageResponse;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertResponse;
import com.survey.meetorsolo.domain.admin.safety.service.AdminSafetyAlertService;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/safety-alerts")
public class AdminSafetyAlertController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final AdminSafetyAlertService alerts;

    public AdminSafetyAlertController(JwtProvider jwtProvider, AdminSafetyAlertService alerts) {
        this.jwtProvider = jwtProvider;
        this.alerts = alerts;
    }

    @GetMapping
    public ApiResponse<AdminSafetyAlertPageResponse> list(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(alerts.list(memberId(accessToken), status, cursor, size));
    }

    /** 알림 확인 처리. 같은 요청을 반복해도 상태가 변하지 않는 멱등 endpoint다. */
    @PutMapping("/{alertId}/acknowledgement")
    public ApiResponse<AdminSafetyAlertResponse> acknowledge(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long alertId
    ) {
        return ApiResponse.success(alerts.acknowledge(memberId(accessToken), alertId));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
