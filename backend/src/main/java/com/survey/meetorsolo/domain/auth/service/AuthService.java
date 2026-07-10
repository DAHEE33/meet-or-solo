package com.survey.meetorsolo.domain.auth.service;

import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.entity.RefreshToken;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.repository.RefreshTokenRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.external.kakao.KakaoOAuthClient;
import com.survey.meetorsolo.external.kakao.dto.KakaoTokenResponse;
import com.survey.meetorsolo.external.kakao.dto.KakaoUserResponse;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    public AuthService(
            KakaoOAuthClient kakaoOAuthClient,
            MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtProvider jwtProvider
    ) {
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
    }

    public URI getKakaoAuthorizeUri(String state) {
        return kakaoOAuthClient.buildAuthorizeUri(state);
    }

    @Transactional
    public AuthTokenResponse loginWithKakao(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        KakaoTokenResponse kakaoToken = kakaoOAuthClient.requestToken(code);
        KakaoUserResponse kakaoUser = kakaoOAuthClient.requestUserInfo(kakaoToken.accessToken());
        Member member = upsertKakaoMember(kakaoUser);

        String accessToken = jwtProvider.createAccessToken(member);
        String refreshToken = jwtProvider.createRefreshToken(member);
        String refreshTokenHash = jwtProvider.hashToken(refreshToken);
        OffsetDateTime refreshTokenExpiresAt = OffsetDateTime.now()
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
