package com.survey.meetorsolo.domain.safety.report.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportRequest;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportResponse;
import com.survey.meetorsolo.domain.safety.report.service.MatchReportService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match-groups")
public class MatchReportController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final MatchReportService reports;

    public MatchReportController(JwtProvider jwtProvider, MatchReportService reports) {
        this.jwtProvider = jwtProvider;
        this.reports = reports;
    }

    @PostMapping("/{groupId}/reports")
    public ResponseEntity<ApiResponse<MatchReportResponse>> submit(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long groupId,
            @Valid @RequestBody MatchReportRequest request
    ) {
        MatchReportResponse response = reports.submit(
                memberId(accessToken), groupId, request.reportedMemberId(), request.reasonCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
