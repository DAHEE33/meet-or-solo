package com.survey.meetorsolo.domain.festival.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.festival.dto.*;
import com.survey.meetorsolo.domain.festival.service.FestivalMeetingPointAdminService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/festivals/{festivalId}/meeting-points")
public class AdminFestivalMeetingPointController {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private final JwtProvider jwtProvider;
    private final FestivalMeetingPointAdminService service;

    public AdminFestivalMeetingPointController(JwtProvider jwtProvider,
            FestivalMeetingPointAdminService service) {
        this.jwtProvider = jwtProvider;
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<FestivalMeetingPointResponse>> list(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String token,
            @PathVariable long festivalId) {
        return ApiResponse.success(service.list(memberId(token), festivalId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FestivalMeetingPointResponse>> create(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String token,
            @PathVariable long festivalId,
            @Valid @RequestBody FestivalMeetingPointUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(memberId(token), festivalId, request)));
    }

    @PutMapping("/{pointId}")
    public ApiResponse<FestivalMeetingPointResponse> update(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String token,
            @PathVariable long festivalId, @PathVariable long pointId,
            @Valid @RequestBody FestivalMeetingPointUpsertRequest request) {
        return ApiResponse.success(service.update(memberId(token), festivalId, pointId, request));
    }

    @PatchMapping("/{pointId}/status")
    public ApiResponse<FestivalMeetingPointResponse> changeStatus(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String token,
            @PathVariable long festivalId, @PathVariable long pointId,
            @Valid @RequestBody FestivalMeetingPointStatusRequest request) {
        return ApiResponse.success(service.changeStatus(memberId(token), festivalId, pointId, request.status()));
    }

    private long memberId(String token) {
        if (token == null || token.isBlank()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return jwtProvider.getMemberIdFromAccessToken(token);
    }
}
