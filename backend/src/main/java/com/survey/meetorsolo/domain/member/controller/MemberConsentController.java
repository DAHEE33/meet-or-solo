package com.survey.meetorsolo.domain.member.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.dto.AgreeConsentRequest;
import com.survey.meetorsolo.domain.member.dto.MemberConsentResponse;
import com.survey.meetorsolo.domain.member.dto.MemberConsentsResponse;
import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import com.survey.meetorsolo.domain.member.service.MemberConsentService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/consents")
public class MemberConsentController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final MemberConsentService consentService;
    private final JwtProvider jwtProvider;

    public MemberConsentController(MemberConsentService consentService, JwtProvider jwtProvider) {
        this.consentService = consentService;
        this.jwtProvider = jwtProvider;
    }

    /** 취향 분석에 필요한 동의 상태를 조회한다. 기록이 없는 유형도 항목으로 내려간다. */
    @GetMapping
    public ApiResponse<MemberConsentsResponse> get(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(consentService.getAiConsents(memberId(accessToken)));
    }

    /** 동의를 기록한다. 고지 문구 버전은 클라이언트가 아니라 서버가 정한다. */
    @PostMapping
    public ApiResponse<MemberConsentResponse> agree(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody AgreeConsentRequest request
    ) {
        return ApiResponse.success(consentService.agree(
                memberId(accessToken), MemberConsentType.from(request.consentType())));
    }

    @DeleteMapping("/{consentType}")
    public ApiResponse<MemberConsentResponse> revoke(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable String consentType
    ) {
        return ApiResponse.success(consentService.revoke(
                memberId(accessToken), MemberConsentType.from(consentType)));
    }

    private Long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
