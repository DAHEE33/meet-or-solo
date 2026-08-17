package com.survey.meetorsolo.domain.festival.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.festival.dto.CurrentCheckinResponse;
import com.survey.meetorsolo.domain.festival.service.FestivalCheckinService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 회원 본인의 현재 체크인 조회·취소. {@code /matching} 화면이 "체크인하기" 버튼 대신
 * 어느 축제에 체크인되어 있는지 보여주고, 매칭 신청 전(IDLE)에 취소할 수 있게 한다.
 */
@RestController
@RequestMapping("/api/festivals/checkin/me")
public class FestivalCheckinMeController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final FestivalCheckinService festivalCheckinService;
    private final JwtProvider jwtProvider;

    public FestivalCheckinMeController(FestivalCheckinService festivalCheckinService, JwtProvider jwtProvider) {
        this.festivalCheckinService = festivalCheckinService;
        this.jwtProvider = jwtProvider;
    }

    @GetMapping
    public ApiResponse<CurrentCheckinResponse> getCurrentCheckin(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        Long memberId = memberId(accessToken);
        return ApiResponse.success(festivalCheckinService.getCurrentCheckin(memberId).orElse(null));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancelCurrentCheckin(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        Long memberId = memberId(accessToken);
        festivalCheckinService.cancelCurrentCheckin(memberId);
        return ResponseEntity.noContent().build();
    }

    private Long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
