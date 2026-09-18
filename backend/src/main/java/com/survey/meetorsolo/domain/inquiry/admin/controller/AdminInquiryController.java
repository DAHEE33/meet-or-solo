package com.survey.meetorsolo.domain.inquiry.admin.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryDetailResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryPageResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryUpdateRequest;
import com.survey.meetorsolo.domain.inquiry.admin.service.AdminInquiryService;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryMessageCreateRequest;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 1:1 문의 관리. {@code AdminReportController}와 같은 형태다.
 *
 * <p>{@code /api/admin/**}은 {@code SuspendedActivityPolicy} 판정 대상이 아니다 —
 * {@code AdminAuthorizationService}가 {@code requireAccessible}로 정지 회원을 이미 차단한다.
 */
@RestController
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final AdminInquiryService inquiries;

    public AdminInquiryController(JwtProvider jwtProvider, AdminInquiryService inquiries) {
        this.jwtProvider = jwtProvider;
        this.inquiries = inquiries;
    }

    @GetMapping
    public ApiResponse<AdminInquiryPageResponse> list(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(inquiries.list(
                memberId(accessToken), status, category, priority,
                createdFrom, createdTo, cursor, size));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<AdminInquiryDetailResponse> detail(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long inquiryId
    ) {
        return ApiResponse.success(inquiries.detail(memberId(accessToken), inquiryId));
    }

    /** 답변. 등록되면 상태가 {@code ANSWERED}로 바뀌고 사용자 badge가 켜진다. */
    @PostMapping("/{inquiryId}/messages")
    public ApiResponse<AdminInquiryDetailResponse> answer(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long inquiryId,
            @Valid @RequestBody InquiryMessageCreateRequest request
    ) {
        return ApiResponse.success(
                inquiries.answer(memberId(accessToken), inquiryId, request.body()));
    }

    /** 상태·우선순위 변경. 긴급 지정이 관리자 전용이라 한 endpoint에서 함께 받는다. */
    @PatchMapping("/{inquiryId}")
    public ApiResponse<AdminInquiryDetailResponse> update(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long inquiryId,
            @RequestBody AdminInquiryUpdateRequest request
    ) {
        return ApiResponse.success(inquiries.update(
                memberId(accessToken), inquiryId, request.status(), request.priority()));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
