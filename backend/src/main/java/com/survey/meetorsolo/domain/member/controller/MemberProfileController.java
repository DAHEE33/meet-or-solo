package com.survey.meetorsolo.domain.member.controller;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.dto.MemberProfileResponse;
import com.survey.meetorsolo.domain.member.dto.UpdateMemberProfileRequest;
import com.survey.meetorsolo.domain.member.service.MemberProfileService;
import com.survey.meetorsolo.domain.member.service.MemberProfileImageService;
import com.survey.meetorsolo.domain.member.service.MemberWithdrawalService;
import com.survey.meetorsolo.external.objectstorage.StoredObject;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/members/me")
public class MemberProfileController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final MemberProfileService memberProfileService;
    private final MemberProfileImageService memberProfileImageService;
    private final MemberWithdrawalService memberWithdrawalService;
    private final JwtProvider jwtProvider;
    private final boolean secureCookies;

    public MemberProfileController(
            MemberProfileService memberProfileService,
            MemberProfileImageService memberProfileImageService,
            MemberWithdrawalService memberWithdrawalService,
            JwtProvider jwtProvider,
            @Value("${app.auth.cookie-secure}") boolean secureCookies
    ) {
        this.memberProfileService = memberProfileService;
        this.memberProfileImageService = memberProfileImageService;
        this.memberWithdrawalService = memberWithdrawalService;
        this.jwtProvider = jwtProvider;
        this.secureCookies = secureCookies;
    }

    @PostMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MemberProfileResponse> uploadProfileImage(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(memberProfileImageService.upload(memberId(accessToken), file));
    }

    @GetMapping("/profile-image")
    public ResponseEntity<byte[]> getProfileImage(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        StoredObject object = memberProfileImageService.download(memberId(accessToken));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(object.content());
    }

    @GetMapping
    public ApiResponse<MemberProfileResponse> getProfile(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        return ApiResponse.success(memberProfileService.getProfile(memberId(accessToken)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> completeProfile(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @Valid @RequestBody UpdateMemberProfileRequest request
    ) {
        MemberProfileResponse response = memberProfileService.completeProfile(memberId(accessToken), request);
        String renewedAccessToken = jwtProvider.createAccessToken(response.memberId(), response.status());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie(renewedAccessToken).toString())
                .body(ApiResponse.success(response));
    }

    /**
     * 회원 본인 탈퇴({@code docs/19} 4.4).
     *
     * <p>세션이 폐기되므로 응답에서 access/refresh cookie를 함께 만료시킨다. 로그아웃과 같은
     * 방식이다. cookie를 남겨두면 프론트엔드가 탈퇴 직후에도 로그인 상태로 보인다.
     *
     * <p>반복 호출은 조용히 성공한다. 다만 첫 호출로 상태가 {@code WITHDRAWN}이 되면
     * {@code MemberAccessInterceptor}가 다음 요청을 먼저 막으므로, 실제로 재도달하는
     * 경우는 같은 access token으로 동시에 들어온 요청뿐이다.
     */
    @DeleteMapping
    public ResponseEntity<Void> withdraw(
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken
    ) {
        memberWithdrawalService.withdrawSelf(memberId(accessToken));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredTokenCookie(ACCESS_TOKEN_COOKIE).toString())
                .header(HttpHeaders.SET_COOKIE, expiredTokenCookie(REFRESH_TOKEN_COOKIE).toString())
                .build();
    }

    private Long memberId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return jwtProvider.getMemberIdFromAccessToken(accessToken);
    }

    private ResponseCookie expiredTokenCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie accessTokenCookie(String token) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(jwtProvider.getAccessTokenExpiresInSeconds()))
                .build();
    }
}
