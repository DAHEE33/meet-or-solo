package com.survey.meetorsolo.domain.auth.service;

import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.entity.RefreshToken;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.repository.RefreshTokenRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import com.survey.meetorsolo.external.kakao.KakaoOAuthClient;
import com.survey.meetorsolo.external.kakao.dto.KakaoTokenResponse;
import com.survey.meetorsolo.external.kakao.dto.KakaoUserResponse;
import com.survey.meetorsolo.external.naver.NaverOAuthClient;
import com.survey.meetorsolo.external.naver.dto.NaverTokenResponse;
import com.survey.meetorsolo.external.naver.dto.NaverUserResponse;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final NaverOAuthClient naverOAuthClient;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    public AuthService(
            KakaoOAuthClient kakaoOAuthClient,
            NaverOAuthClient naverOAuthClient,
            MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtProvider jwtProvider
    ) {
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.naverOAuthClient = naverOAuthClient;
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
    }

    public URI getKakaoAuthorizeUri(String state) {
        return kakaoOAuthClient.buildAuthorizeUri(state);
    }

    public URI getNaverAuthorizeUri(String state) {
        return naverOAuthClient.buildAuthorizeUri(state);
    }

    @Transactional
    public AuthTokenResponse loginWithKakao(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        KakaoTokenResponse kakaoToken = kakaoOAuthClient.requestToken(code);
        KakaoUserResponse kakaoUser = kakaoOAuthClient.requestUserInfo(kakaoToken.accessToken());
        return issueTokens(upsertKakaoMember(kakaoUser));
    }

    @Transactional
    public AuthTokenResponse loginWithNaver(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        NaverTokenResponse naverToken = naverOAuthClient.requestToken(code, state);
        NaverUserResponse naverUser = naverOAuthClient.requestUserInfo(naverToken.accessToken());
        return issueTokens(upsertNaverMember(naverUser));
    }

    private AuthTokenResponse issueTokens(Member member) {

        String accessToken = jwtProvider.createAccessToken(member);
        String refreshToken = jwtProvider.createRefreshToken(member);
        String refreshTokenHash = jwtProvider.hashToken(refreshToken);
        OffsetDateTime refreshTokenExpiresAt = SeoulDateTime.now()
                .plusSeconds(jwtProvider.getRefreshTokenExpiresInSeconds());

        refreshTokenRepository.save(RefreshToken.issue(member, refreshTokenHash, refreshTokenExpiresAt));

        return new AuthTokenResponse(
                "Bearer",
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpiresInSeconds(),
                jwtProvider.getRefreshTokenExpiresInSeconds(),
                member.getId(),
                member.getStatus()
        );
    }

    private Member upsertNaverMember(NaverUserResponse naverUser) {
        return memberRepository.findByProviderAndProviderUserId(Member.PROVIDER_NAVER, naverUser.providerUserId())
                .map(member -> {
                    member.updateNaverProfile(naverUser.nickname(), naverUser.profileImageUrl());
                    return member;
                })
                .orElseGet(() -> memberRepository.save(Member.createNaverMember(
                        naverUser.providerUserId(),
                        naverUser.nickname(),
                        naverUser.profileImageUrl()
                )));
    }

    private Member upsertKakaoMember(KakaoUserResponse kakaoUser) {
        return memberRepository.findByProviderAndProviderUserId(Member.PROVIDER_KAKAO, kakaoUser.providerUserId())
                .map(member -> {
                    member.updateKakaoProfile(kakaoUser.nickname(), kakaoUser.profileImageUrl());
                    return member;
                })
                .orElseGet(() -> memberRepository.save(Member.createKakaoMember(
                        kakaoUser.providerUserId(),
                        kakaoUser.nickname(),
                        kakaoUser.profileImageUrl()
                )));
    }
}
