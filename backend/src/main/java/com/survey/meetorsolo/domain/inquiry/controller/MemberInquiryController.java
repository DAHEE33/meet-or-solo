package com.survey.meetorsolo.domain.inquiry.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryCreateRequest;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryDetailResponse;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryListResponse;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryMessageCreateRequest;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryUnreadCountResponse;
import com.survey.meetorsolo.domain.inquiry.service.InquiryService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 1:1 문의. 모두 인증이 필요하다.
 *
 * <p>등록·추가 질문은 {@code SuspendedActivityPolicy}의 허용 목록에 등재되어 정지 회원도
 * 사용할 수 있다. 새 endpoint를 추가하면 그 목록에 반드시 분류해야
 * {@code SuspendedActivityPolicyCoverageTest}가 통과한다(docs/29 2.3).
 */
@RestController
@RequestMapping("/api/members/me/inquiries")
public class MemberInquiryController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final int MAX_SIZE = 50;

    private final InquiryService inquiries;
    private final JwtProvider jwtProvider;

    public MemberInquiryController(InquiryService inquiries, JwtProvider jwtProvider) {
        this.inquiries = inquiries;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InquiryDetailResponse>> create(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody InquiryCreateRequest request
    ) {
        InquiryDetailResponse created = inquiries.create(
                memberId(accessToken), request.category(), request.title(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    public ApiResponse<InquiryListResponse> list(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                inquiries.getMyInquiries(memberId(accessToken), page(page), size(size)));
    }

    /** {@code MyPage} badge용. 목록 전체를 불러오지 않고 숫자만 읽는다(docs/29 2.2). */
    @GetMapping("/unread-count")
    public ApiResponse<InquiryUnreadCountResponse> unreadCount(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(inquiries.getUnreadAnswerCount(memberId(accessToken)));
    }

    /**
     * 스레드 상세. 조회 시 열람 시각을 갱신하므로 상태를 바꾼다.
     *
     * <p>이 요청은 {@code SuspendedActivityPolicy}의 목록 대상이 아니다 — 전수 조사 테스트는
     * {@code GET}을 상태 변경 요청으로 보지 않는다. 정지 회원도 자기 문의를 볼 수 있어야 하므로
     * 그 판정이 의도와 일치한다.
     */
    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryDetailResponse> detail(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long inquiryId
    ) {
        return ApiResponse.success(inquiries.getMyInquiry(memberId(accessToken), inquiryId));
    }

    @PostMapping("/{inquiryId}/messages")
    public ResponseEntity<ApiResponse<InquiryDetailResponse>> addMessage(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long inquiryId,
            @Valid @RequestBody InquiryMessageCreateRequest request
    ) {
        InquiryDetailResponse updated = inquiries.addMessage(
                memberId(accessToken), inquiryId, request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(updated));
    }

    private static int page(int value) {
        if (value < 0) {
            throw new BusinessException(ErrorCode.INQUIRY_INVALID_REQUEST, "page는 0 이상이어야 합니다.");
        }
        return value;
    }

    private static int size(int value) {
        if (value < 1 || value > MAX_SIZE) {
            throw new BusinessException(
                    ErrorCode.INQUIRY_INVALID_REQUEST, "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
        return value;
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
