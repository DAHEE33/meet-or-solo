package com.survey.meetorsolo.domain.safety.block.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.safety.block.dto.MemberBlockResponse;
import com.survey.meetorsolo.domain.safety.block.service.MemberBlockService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/blocks")
public class MemberBlockController {
    private final JwtProvider jwtProvider;
    private final MemberBlockService blocks;

    public MemberBlockController(JwtProvider jwtProvider, MemberBlockService blocks) {
        this.jwtProvider = jwtProvider;
        this.blocks = blocks;
    }

    @GetMapping
    public ApiResponse<List<MemberBlockResponse>> getMyBlocks(
            @CookieValue(name = "access_token", required = false) String accessToken) {
        return ApiResponse.success(blocks.getMyBlocks(memberId(accessToken)));
    }

    @DeleteMapping("/{blockedMemberId}")
    public ResponseEntity<Void> unblock(
            @CookieValue(name = "access_token", required = false) String accessToken,
            @PathVariable long blockedMemberId) {
        blocks.unblock(memberId(accessToken), blockedMemberId);
        return ResponseEntity.noContent().build();
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
