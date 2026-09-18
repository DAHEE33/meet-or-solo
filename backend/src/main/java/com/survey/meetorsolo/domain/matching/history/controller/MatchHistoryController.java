package com.survey.meetorsolo.domain.matching.history.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.matching.history.dto.MatchHistoryResponse;
import com.survey.meetorsolo.domain.matching.history.service.MatchHistoryService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/match-history")
public class MatchHistoryController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final MatchHistoryService history;

    public MatchHistoryController(JwtProvider jwtProvider, MatchHistoryService history) {
        this.jwtProvider = jwtProvider;
        this.history = history;
    }

    // 조회 대상은 JWT의 회원으로 고정한다. 회원 ID를 요청에서 받지 않아 타인 이력을 열 수 없다.
    @GetMapping
    public ApiResponse<MatchHistoryResponse> getMyHistory(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(history.getMyHistory(memberId(accessToken), cursor, size));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
