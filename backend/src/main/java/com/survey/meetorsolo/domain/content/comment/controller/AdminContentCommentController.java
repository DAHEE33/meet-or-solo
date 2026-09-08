package com.survey.meetorsolo.domain.content.comment.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.content.comment.dto.AdminContentCommentVisibilityRequest;
import com.survey.meetorsolo.domain.content.comment.dto.AdminContentCommentVisibilityResponse;
import com.survey.meetorsolo.domain.content.comment.service.AdminContentCommentService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 댓글 숨김·재공개(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 5.6). */
@RestController
@RequestMapping("/api/admin/comments")
public class AdminContentCommentController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final AdminContentCommentService adminComments;
    private final JwtProvider jwtProvider;

    public AdminContentCommentController(
            AdminContentCommentService adminComments,
            JwtProvider jwtProvider
    ) {
        this.adminComments = adminComments;
        this.jwtProvider = jwtProvider;
    }

    @PutMapping("/{commentId}/visibility")
    public ApiResponse<AdminContentCommentVisibilityResponse> changeVisibility(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long commentId,
            @Valid @RequestBody AdminContentCommentVisibilityRequest request
    ) {
        return ApiResponse.success(adminComments.changeVisibility(
                memberId(accessToken),
                commentId,
                request.visible()
        ));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
