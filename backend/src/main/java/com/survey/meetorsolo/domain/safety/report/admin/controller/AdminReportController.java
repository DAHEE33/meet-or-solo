package com.survey.meetorsolo.domain.safety.report.admin.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.report.admin.dto.*;
import com.survey.meetorsolo.domain.safety.report.admin.service.AdminReportService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final AdminReportService reports;

    public AdminReportController(JwtProvider jwtProvider, AdminReportService reports) {
        this.jwtProvider = jwtProvider;
        this.reports = reports;
    }

    @GetMapping
    public ApiResponse<AdminReportPageResponse> list(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(reports.list(
                memberId(accessToken), status, reason, createdFrom, createdTo, cursor, size));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<AdminReportDetailResponse> detail(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long reportId
    ) {
        return ApiResponse.success(reports.detail(memberId(accessToken), reportId));
    }

    @PatchMapping("/{reportId}/status")
    public ApiResponse<AdminReportDetailResponse> changeStatus(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long reportId,
            @Valid @RequestBody AdminReportStatusUpdateRequest request
    ) {
        return ApiResponse.success(reports.changeStatus(
                memberId(accessToken), reportId, request.targetStatus()));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
