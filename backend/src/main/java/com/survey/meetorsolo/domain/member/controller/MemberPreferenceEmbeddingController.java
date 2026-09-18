package com.survey.meetorsolo.domain.member.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.dto.CreatePreferenceEmbeddingRequest;
import com.survey.meetorsolo.domain.member.dto.MemberPreferenceEmbeddingResponse;
import com.survey.meetorsolo.domain.member.service.MemberPreferenceEmbeddingService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/preference-embedding")
public class MemberPreferenceEmbeddingController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final MemberPreferenceEmbeddingService embeddingService;
    private final JwtProvider jwtProvider;

    public MemberPreferenceEmbeddingController(
            MemberPreferenceEmbeddingService embeddingService,
            JwtProvider jwtProvider
    ) {
        this.embeddingService = embeddingService;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping
    public ApiResponse<MemberPreferenceEmbeddingResponse> createOrUpdate(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody CreatePreferenceEmbeddingRequest request
    ) {
        return ApiResponse.success(
                embeddingService.createOrUpdate(memberId(accessToken), request.preferenceText()));
    }

    @GetMapping
    public ApiResponse<MemberPreferenceEmbeddingResponse> get(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(embeddingService.getByMemberId(memberId(accessToken)));
    }

    @DeleteMapping
    public ApiResponse<Void> delete(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        embeddingService.delete(memberId(accessToken));
        return ApiResponse.success(null);
    }

    private Long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
