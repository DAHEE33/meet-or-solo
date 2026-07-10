package com.survey.meetorsolo.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.entity.RefreshToken;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.repository.RefreshTokenRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.external.kakao.KakaoOAuthClient;
import com.survey.meetorsolo.external.naver.NaverOAuthClient;
import com.survey.meetorsolo.external.naver.dto.NaverTokenResponse;
import com.survey.meetorsolo.external.naver.dto.NaverUserResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private final KakaoOAuthClient kakaoClient = mock(KakaoOAuthClient.class);
    private final NaverOAuthClient naverClient = mock(NaverOAuthClient.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final AuthService authService = new AuthService(
            kakaoClient, naverClient, memberRepository, refreshTokenRepository, jwtProvider);

    @BeforeEach
    void token정책() {
        when(jwtProvider.createAccessToken(any(Member.class))).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(any(Member.class))).thenReturn("refresh-token");
        when(jwtProvider.hashToken("refresh-token")).thenReturn("refresh-token-hash");
        when(jwtProvider.getAccessTokenExpiresInSeconds()).thenReturn(1800L);
        when(jwtProvider.getRefreshTokenExpiresInSeconds()).thenReturn(1209600L);
    }

    @Test
    void 신규_네이버_회원을_provider와_providerUserId로_생성하고_hash를_저장한다() {
        NaverUserResponse user = user("naver-id", null, null);
        when(naverClient.requestToken("code", "state"))
                .thenReturn(new NaverTokenResponse("naver-access", "bearer", "3600", null, null));
        when(naverClient.requestUserInfo("naver-access")).thenReturn(user);
        when(memberRepository.findByProviderAndProviderUserId(Member.PROVIDER_NAVER, "naver-id"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.loginWithNaver("code", "state");

        assertThat(response.memberStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
        verify(memberRepository).save(any(Member.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(jwtProvider).hashToken("refresh-token");
    }

    @Test
    void 기존_네이버_회원은_중복_생성하지_않고_lastLoginAt을_갱신한다() {
        Member member = Member.createNaverMember("naver-id", "before", null);
        var previousLoginAt = member.getLastLoginAt();
        when(naverClient.requestToken("code", "state"))
                .thenReturn(new NaverTokenResponse("naver-access", "bearer", "3600", null, null));
        when(naverClient.requestUserInfo("naver-access")).thenReturn(user("naver-id", "after", null));
        when(memberRepository.findByProviderAndProviderUserId(Member.PROVIDER_NAVER, "naver-id"))
                .thenReturn(Optional.of(member));

        authService.loginWithNaver("code", "state");

        verify(memberRepository, never()).save(any(Member.class));
        assertThat(member.getNickname()).isEqualTo("after");
        assertThat(member.getLastLoginAt()).isAfterOrEqualTo(previousLoginAt);
    }

    private NaverUserResponse user(String id, String nickname, String email) {
        return new NaverUserResponse("00", "success", new NaverUserResponse.Profile(
                id, email, nickname, null, null, null, null, null));
    }
}
