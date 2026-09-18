package com.survey.meetorsolo.domain.admin.dashboard.controller;

import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardStatsResponse;
import com.survey.meetorsolo.domain.admin.dashboard.service.AdminDashboardService;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final AdminDashboardService dashboard;

    public AdminDashboardController(JwtProvider jwtProvider, AdminDashboardService dashboard) {
        this.jwtProvider = jwtProvider;
        this.dashboard = dashboard;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminDashboardStatsResponse> stats(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(dashboard.getStats(memberId(accessToken)));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
