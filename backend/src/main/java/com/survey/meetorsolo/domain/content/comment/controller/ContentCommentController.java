package com.survey.meetorsolo.domain.content.comment.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentLikeRequest;
import com.survey.meetorsolo.domain.content.comment.dto.ContentCommentLikeResponse;
import com.survey.meetorsolo.domain.content.comment.service.ContentCommentService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글 1건에 대한 삭제·좋아요. 대상 종류(축제/관광지)와 무관하게 댓글 id만으로 다루므로
 * 상세 화면별 controller와 분리했다(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 4).
 *
 * <p>둘 다 인증이 필요하다. 화면은 비로그인 사용자에게 애초에 이 버튼을 노출하지 않는다.
 */
@RestController
@RequestMapping("/api/comments/{commentId}")
public class ContentCommentController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final ContentCommentService comments;
    private final JwtProvider jwtProvider;

    public ContentCommentController(ContentCommentService comments, JwtProvider jwtProvider) {
        this.comments = comments;
        this.jwtProvider = jwtProvider;
    }

    /** 작성자 본인 삭제. 이미 삭제된 댓글에 다시 호출해도 {@code 204}다(멱등). */
    @DeleteMapping
    public ResponseEntity<Void> deleteComment(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long commentId
    ) {
        comments.delete(memberId(accessToken), commentId);
        return ResponseEntity.noContent().build();
    }

    /** 좋아요 토글. 상태를 그대로 받으므로 연타해도 결과가 같다. */
    @PutMapping("/like")
    public ApiResponse<ContentCommentLikeResponse> toggleLike(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long commentId,
            @Valid @RequestBody ContentCommentLikeRequest request
    ) {
        return ApiResponse.success(comments.toggleLike(
                memberId(accessToken),
                commentId,
                request.liked()
        ));
    }

    private long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }
}
