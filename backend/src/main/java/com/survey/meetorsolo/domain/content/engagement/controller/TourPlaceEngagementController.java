package com.survey.meetorsolo.domain.content.engagement.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.content.bookmark.dto.ContentBookmarkToggleRequest;
import com.survey.meetorsolo.domain.content.bookmark.dto.ContentBookmarkToggleResponse;
import com.survey.meetorsolo.domain.content.bookmark.service.ContentBookmarkService;
import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentCreateRequest;
import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentListResponse;
import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentResponse;
import com.survey.meetorsolo.domain.content.comment.service.ContentCommentService;
import com.survey.meetorsolo.domain.content.engagement.dto.ContentEngagementResponse;
import com.survey.meetorsolo.domain.content.engagement.service.ContentEngagementService;
import com.survey.meetorsolo.domain.content.support.ContentTarget;
import com.survey.meetorsolo.domain.content.support.OptionalMemberResolver;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관광지 상세 화면의 찜·댓글 API. 축제 쪽({@link FestivalEngagementController})과 완전히 대칭이며
 * 대상 종류만 다르다.
 *
 * <p>URL 세그먼트는 기존 규칙대로 {@code /api/spots}이고 path variable은 내부 {@code id}다
 * ({@code contentId} 아님) — docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 4.
 */
@Validated
@RestController
@RequestMapping("/api/spots/{id}")
public class TourPlaceEngagementController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final ContentEngagementService engagements;
    private final ContentBookmarkService bookmarks;
    private final ContentCommentService comments;
    private final OptionalMemberResolver optionalMember;
    private final JwtProvider jwtProvider;

    public TourPlaceEngagementController(
            ContentEngagementService engagements,
            ContentBookmarkService bookmarks,
            ContentCommentService comments,
            OptionalMemberResolver optionalMember,
            JwtProvider jwtProvider
    ) {
        this.engagements = engagements;
        this.bookmarks = bookmarks;
        this.comments = comments;
        this.optionalMember = optionalMember;
        this.jwtProvider = jwtProvider;
    }

    /** 공개. 비로그인이면 {@code bookmarked = false}, {@code viewer.loggedIn = false}로 200이다. */
    @GetMapping("/engagement")
    public ApiResponse<ContentEngagementResponse> getEngagement(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable Long id
    ) {
        return ApiResponse.success(engagements.getEngagement(
                optionalMember.resolveOrNull(accessToken),
                ContentTarget.tourPlace(id)
        ));
    }

    @PutMapping("/bookmark")
    public ApiResponse<ContentBookmarkToggleResponse> toggleBookmark(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable Long id,
            @Valid @RequestBody ContentBookmarkToggleRequest request
    ) {
        return ApiResponse.success(bookmarks.toggle(
                memberId(accessToken),
                ContentTarget.tourPlace(id),
                request.bookmarked()
        ));
    }

    /** 공개. 비로그인이면 모든 항목의 {@code likedByMe}/{@code mine}이 {@code false}다. */
    @GetMapping("/comments")
    public ApiResponse<ContentCommentListResponse> getComments(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        return ApiResponse.success(comments.getComments(
                optionalMember.resolveOrNull(accessToken),
                ContentTarget.tourPlace(id),
                page,
                size
        ));
    }

    @PostMapping("/comments")
    public ResponseEntity<ApiResponse<ContentCommentResponse>> createComment(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable Long id,
            @Valid @RequestBody ContentCommentCreateRequest request
    ) {
        ContentCommentResponse created = comments.create(
                memberId(accessToken),
                ContentTarget.tourPlace(id),
                request.body()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
