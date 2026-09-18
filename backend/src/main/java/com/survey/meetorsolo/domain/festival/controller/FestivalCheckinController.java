package com.survey.meetorsolo.domain.festival.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.service.FestivalCheckinService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/festivals/{festivalId}/checkin")
public class FestivalCheckinController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final FestivalCheckinService festivalCheckinService;
    private final JwtProvider jwtProvider;

    public FestivalCheckinController(FestivalCheckinService festivalCheckinService, JwtProvider jwtProvider) {
        this.festivalCheckinService = festivalCheckinService;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping
    public ApiResponse<FestivalCheckinResponse> checkIn(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable Long festivalId,
            @Valid @RequestBody CheckInRequest request
    ) {
        Long memberId = memberId(accessToken);
        return ApiResponse.success(festivalCheckinService.checkIn(memberId, festivalId, request));
    }

    private Long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
