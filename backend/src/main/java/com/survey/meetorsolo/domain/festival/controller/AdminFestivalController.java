package com.survey.meetorsolo.domain.festival.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.festival.dto.AdminFestivalSummaryResponse;
import com.survey.meetorsolo.domain.festival.service.FestivalAdminQueryService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 만남 장소 화면의 "축제 선택" 검색 전용. 공개 {@link FestivalController}와 달리
 * 종료된 축제도 포함한다 — {@link FestivalAdminQueryService} 참고.
 */
@RestController
@RequestMapping("/api/admin/festivals")
public class AdminFestivalController {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;
    private final FestivalAdminQueryService service;

    public AdminFestivalController(JwtProvider jwtProvider, FestivalAdminQueryService service) {
        this.jwtProvider = jwtProvider;
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AdminFestivalSummaryResponse>> search(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String token,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(service.search(memberId(token), keyword));
    }

    private long memberId(String token) {
        if (token == null || token.isBlank()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return jwtProvider.getMemberIdFromAccessToken(token);
    }
}
