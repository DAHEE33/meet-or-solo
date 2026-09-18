package com.survey.meetorsolo.domain.admin.diagnostics.controller;

import com.survey.meetorsolo.domain.admin.diagnostics.dto.EmbeddingDiagnosticsResponse;
import com.survey.meetorsolo.domain.admin.diagnostics.service.EmbeddingDiagnosticsService;
import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 진단 endpoint. 외부 연동이 지금 되는지 관리자만 확인한다.
 */
@RestController
@RequestMapping("/api/admin/diagnostics")
public class AdminDiagnosticsController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final AdminAuthorizationService authorization;
    private final EmbeddingDiagnosticsService diagnostics;

    public AdminDiagnosticsController(
            JwtProvider jwtProvider,
            AdminAuthorizationService authorization,
            EmbeddingDiagnosticsService diagnostics
    ) {
        this.jwtProvider = jwtProvider;
        this.authorization = authorization;
        this.diagnostics = diagnostics;
    }

    /**
     * 취향 임베딩 연결 확인. 실패해도 200으로 결과를 담아 돌려준다 — 실패 이유가 곧 응답이다.
     */
    @GetMapping("/embedding")
    public ApiResponse<EmbeddingDiagnosticsResponse> embedding(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        authorization.requireAdmin(memberId(accessToken));
        return ApiResponse.success(diagnostics.probe());
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
