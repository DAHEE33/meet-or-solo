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

    /**
     * 관리자 강제 탈퇴({@code docs/19} 4.4).
     *
     * <p>제재 조치({@code /actions})와 endpoint를 분리한다. {@code BAN}은 되돌릴 수 있고
     * 강제 탈퇴는 익명화라 되돌릴 수 없다.
     */
    @PostMapping("/{memberId}/forced-withdrawal")
    public ApiResponse<AdminMemberDetailResponse> forceWithdraw(
            @CookieValue(name = "access_token", required = false) String accessToken,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable long memberId,
            @Valid @RequestBody AdminMemberForcedWithdrawalRequest request
    ) {
        return ApiResponse.success(members.forceWithdraw(
                memberId(accessToken), memberId, idempotencyKey, request));
    }

    /**
     * 관리자 매너온도 수동 조정({@code docs/19} 4.9).
     *
     * <p>제재 조치({@code /actions})와 endpoint를 분리한다. 제재는 회원 상태를 바꾸고
     * 온도 조정은 상태를 바꾸지 않는다. 매너온도는 신고 확정으로만 내려가는 하강 전용
     * 지표였으므로 이 endpoint가 유일한 복구 경로다.
     */
    @PostMapping("/{memberId}/manner-temperature")
    public ApiResponse<AdminMemberDetailResponse> adjustMannerTemperature(
            @CookieValue(name = "access_token", required = false) String accessToken,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable long memberId,
            @Valid @RequestBody AdminMemberMannerTemperatureRequest request
    ) {
        return ApiResponse.success(members.adjustMannerTemperature(
                memberId(accessToken), memberId, idempotencyKey, request));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
