package com.survey.meetorsolo.domain.content.bookmark.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.content.bookmark.dto.BookmarkedContentListResponse;
import com.survey.meetorsolo.domain.content.bookmark.service.ContentBookmarkService;
import com.survey.meetorsolo.domain.content.support.ContentTargetType;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 찜 목록. 인증 필수이며 본인 것만 조회한다 — 다른 회원을 지정하는 계약을 제공하지 않는다
 * (docs/06_SECURITY_POLICY.md의 {@code /me} 기조).
 *
 * <p>{@code /api/members/me/blocks}와 같은 계층에 둔다.
 */
@Validated
@RestController
@RequestMapping("/api/members/me/bookmarks")
public class MemberBookmarkController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final ContentBookmarkService bookmarks;
    private final JwtProvider jwtProvider;

    public MemberBookmarkController(ContentBookmarkService bookmarks, JwtProvider jwtProvider) {
        this.bookmarks = bookmarks;
        this.jwtProvider = jwtProvider;
    }

    @GetMapping
    public ApiResponse<BookmarkedContentListResponse> getMyBookmarks(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestParam(defaultValue = "FESTIVAL") ContentTargetType type,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        return ApiResponse.success(
                bookmarks.getMyBookmarks(memberId(accessToken), type, page, size)
        );
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
