package com.survey.meetorsolo.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.entity.RefreshToken;
import com.survey.meetorsolo.domain.auth.event.MemberLoggedOutEvent;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.repository.RefreshTokenRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.domain.member.service.MemberRejoinPolicy;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.external.kakao.KakaoOAuthClient;
import com.survey.meetorsolo.external.naver.NaverOAuthClient;
import com.survey.meetorsolo.external.naver.dto.NaverTokenResponse;
import com.survey.meetorsolo.external.naver.dto.NaverUserResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private final KakaoOAuthClient kakaoClient = mock(KakaoOAuthClient.class);
    private final NaverOAuthClient naverClient = mock(NaverOAuthClient.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final MemberAccessPolicy accessPolicy = mock(MemberAccessPolicy.class);
    private final MemberRejoinPolicy rejoinPolicy = mock(MemberRejoinPolicy.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuthService authService = new AuthService(
            kakaoClient, naverClient, memberRepository, refreshTokenRepository, jwtProvider, accessPolicy,
            rejoinPolicy, events);

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

    @Test
    void 재로그인하면_기존_RefreshToken_row를_rotation한다() {
        Member member = Member.createNaverMember("naver-id", "nickname", null);
        RefreshToken existingToken = RefreshToken.issue(member, "old-hash", SeoulDateTime.now().plusDays(1));
        when(naverClient.requestToken("code", "state"))
                .thenReturn(new NaverTokenResponse("naver-access", "bearer", "3600", null, null));
        when(naverClient.requestUserInfo("naver-access")).thenReturn(user("naver-id", "nickname", null));
        when(memberRepository.findByProviderAndProviderUserId(Member.PROVIDER_NAVER, "naver-id"))
                .thenReturn(Optional.of(member));
        when(refreshTokenRepository.findByMemberId(member.getId())).thenReturn(Optional.of(existingToken));

        authService.loginWithNaver("code", "state");

        verify(refreshTokenRepository).save(existingToken);
    }

    @Test
    void 로그아웃은_refresh_token을_폐기하고_session_종료_event를_발행한다() {
        when(jwtProvider.getMemberIdFromAccessToken("access-token")).thenReturn(7L);

        authService.logout("access-token");

        verify(refreshTokenRepository).revokeByMemberId(eq(7L), any(OffsetDateTime.class));
        verify(events).publishEvent(new MemberLoggedOutEvent(7L));
    }

    @Test
    void 이미_폐기된_상태에서_다시_로그아웃해도_예외없이_멱등하다() {
        when(jwtProvider.getMemberIdFromAccessToken("access-token")).thenReturn(7L);
        when(refreshTokenRepository.revokeByMemberId(eq(7L), any(OffsetDateTime.class))).thenReturn(0);

        authService.logout("access-token");
        authService.logout("access-token");

        verify(refreshTokenRepository, times(2)).revokeByMemberId(eq(7L), any(OffsetDateTime.class));
        verify(events, times(2)).publishEvent(new MemberLoggedOutEvent(7L));
    }

    @Test
    void 토큰이_없으면_로그아웃은_아무것도_폐기하지_않는다() {
        authService.logout(null);
        authService.logout("  ");

        verifyNoInteractions(refreshTokenRepository, events);
    }

    @Test
    void 만료되거나_변조된_토큰이면_로그아웃은_조용히_종료한다() {
        when(jwtProvider.getMemberIdFromAccessToken("broken"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        authService.logout("broken");

        verify(refreshTokenRepository, never()).revokeByMemberId(anyLong(), any(OffsetDateTime.class));
        verifyNoInteractions(events);
    }

    private NaverUserResponse user(String id, String nickname, String email) {
        return new NaverUserResponse("00", "success", new NaverUserResponse.Profile(
                id, email, nickname, null, null, null, null, null));
    }
}
