package com.survey.meetorsolo.domain.admin.member.controller;

import com.survey.meetorsolo.domain.admin.member.dto.*;
import com.survey.meetorsolo.domain.admin.member.service.AdminMemberService;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final JwtProvider jwtProvider;
    private final AdminMemberService members;

    public AdminMemberController(JwtProvider jwtProvider, AdminMemberService members) {
        this.jwtProvider = jwtProvider;
        this.members = members;
    }

    @GetMapping
    public ApiResponse<AdminMemberPageResponse> list(
            @CookieValue(name = "access_token", required = false) String accessToken,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(members.list(
                memberId(accessToken), query, status, role, cursor, size));
    }

    @GetMapping("/{memberId}")
    public ApiResponse<AdminMemberDetailResponse> detail(
            @CookieValue(name = "access_token", required = false) String accessToken,
            @PathVariable long memberId
    ) {
        return ApiResponse.success(members.detail(memberId(accessToken), memberId));
    }

    @PostMapping("/{memberId}/actions")
    public ApiResponse<AdminMemberDetailResponse> act(
            @CookieValue(name = "access_token", required = false) String accessToken,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable long memberId,
            @Valid @RequestBody AdminMemberActionRequest request
    ) {
        return ApiResponse.success(members.act(
                memberId(accessToken), memberId, idempotencyKey, request));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
