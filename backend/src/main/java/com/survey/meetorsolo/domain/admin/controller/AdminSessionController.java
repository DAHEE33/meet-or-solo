package com.survey.meetorsolo.domain.admin.controller;

import com.survey.meetorsolo.domain.admin.dto.AdminSessionResponse;
import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminSessionController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final AdminAuthorizationService authorization;

    public AdminSessionController(JwtProvider jwtProvider, AdminAuthorizationService authorization) {
        this.jwtProvider = jwtProvider;
        this.authorization = authorization;
    }

    @GetMapping("/me")
    public ApiResponse<AdminSessionResponse> me(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(AdminSessionResponse.from(
                authorization.requireAdmin(memberId(accessToken))));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
