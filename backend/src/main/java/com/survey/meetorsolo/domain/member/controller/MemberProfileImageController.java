package com.survey.meetorsolo.domain.member.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.service.MemberProfileImageService;
import com.survey.meetorsolo.external.objectstorage.StoredObject;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 회원이 직접 올린 프로필 사진을 내려준다.
 *
 * <p>private bucket에 있는 사진은 {@code GET /api/members/me/profile-image} 본인 전용 경로
 * 하나로만 꺼낼 수 있었다. 그래서 사진을 올려도 댓글·매칭방 등 <b>남이 보는 자리에는 한 번도
 * 보이지 않았다</b>. 이 경로가 그 자리를 메운다.
 *
 * <p><b>로그인한 회원만 부를 수 있다.</b> 댓글은 비로그인도 읽을 수 있는 화면이지만, 얼굴
 * 사진과 소셜 프로필 사진을 비회원·크롤러에게까지 열지는 않기로 했다
 * ({@code docs/27} 6.2). 비로그인 화면에는 URL 자체를 담지 않고, 여기서도 한 번 더 막는다 —
 * 화면이 값을 감추는 것과 서버가 거절하는 것은 다른 일이다.
 *
 * <p>{@code SecurityConfig}가 {@code anyRequest().permitAll()}이라 인증은 filter가 아니라
 * 이 controller가 직접 한다. 프로젝트의 다른 회원 API와 같은 방식이다.
 *
 * <p>대상이 누구든 <b>같은 응답을 준다</b> — 사진이 없거나 없는 회원이면 똑같이
 * {@code PROFILE_IMAGE_NOT_FOUND}다. 구분해서 알리면 "그 id의 회원이 있는지"가 응답으로
 * 드러난다({@code AdminAuthorizationService}가 사유를 갈라 알리지 않는 것과 같은 이유다).
 */
@RestController
public class MemberProfileImageController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final MemberProfileImageService memberProfileImageService;
    private final JwtProvider jwtProvider;

    public MemberProfileImageController(
            MemberProfileImageService memberProfileImageService,
            JwtProvider jwtProvider
    ) {
        this.memberProfileImageService = memberProfileImageService;
        this.jwtProvider = jwtProvider;
    }

    @GetMapping("/api/members/{memberId}/profile-image")
    public ResponseEntity<byte[]> getProfileImage(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @PathVariable long memberId
    ) {
        requireLogin(accessToken);
        StoredObject object = download(memberId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.contentType()))
                // 본인 전용 경로와 같은 no-store다. 프로필 사진은 바뀔 수 있고, 공용 캐시에
                // 남으면 로그인 여부와 무관하게 다시 나갈 수 있다.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(object.content());
    }

    private void requireLogin(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 토큰이 유효한지만 본다. 누구인지는 쓰지 않는다 — 로그인 회원이면 서로의 사진을 본다.
        jwtProvider.getMemberIdFromAccessToken(accessToken);
    }

    private StoredObject download(long memberId) {
        try {
            return memberProfileImageService.download(memberId);
        } catch (BusinessException failure) {
            if (failure.getErrorCode() == ErrorCode.NOT_FOUND) {
                throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_FOUND);
            }
            throw failure;
        }
    }
}
