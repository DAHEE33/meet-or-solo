package com.survey.meetorsolo.domain.safety.block.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.block.dto.MatchBlockRequest;
import com.survey.meetorsolo.domain.safety.block.dto.MatchBlockResponse;
import com.survey.meetorsolo.domain.safety.block.service.MatchBlockService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match-groups")
public class MatchBlockController {

    private final JwtProvider jwtProvider;
    private final MatchBlockService blocks;

    public MatchBlockController(JwtProvider jwtProvider, MatchBlockService blocks) {
        this.jwtProvider = jwtProvider;
        this.blocks = blocks;
    }

    @PostMapping("/{groupId}/blocks")
    public ResponseEntity<ApiResponse<MatchBlockResponse>> block(
            @CookieValue(name = "access_token", required = false) String accessToken,
            @PathVariable long groupId,
            @Valid @RequestBody MatchBlockRequest request
    ) {
        MatchBlockResponse response = blocks.block(
                memberId(accessToken), groupId, request.blockedMemberId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
