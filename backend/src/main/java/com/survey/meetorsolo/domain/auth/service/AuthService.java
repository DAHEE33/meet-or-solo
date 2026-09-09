package com.survey.meetorsolo.domain.auth.service;

import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.entity.RefreshToken;
import com.survey.meetorsolo.domain.auth.event.MemberLoggedOutEvent;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.repository.RefreshTokenRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.domain.member.service.MemberRejoinPolicy;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final NaverOAuthClient naverOAuthClient;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final MemberAccessPolicy accessPolicy;
    private final MemberRejoinPolicy rejoinPolicy;
    private final ApplicationEventPublisher events;

    public AuthService(
            KakaoOAuthClient kakaoOAuthClient,
            NaverOAuthClient naverOAuthClient,
            MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtProvider jwtProvider,
            MemberAccessPolicy accessPolicy,
            MemberRejoinPolicy rejoinPolicy,
            ApplicationEventPublisher events
    ) {
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.naverOAuthClient = naverOAuthClient;
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
        this.accessPolicy = accessPolicy;
        this.rejoinPolicy = rejoinPolicy;
        this.events = events;
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

    @Transactional
    public AuthTokenResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        long memberId = jwtProvider.getMemberIdFromRefreshToken(rawRefreshToken);
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        // 정지 회원의 session을 끊으면 조회조차 못 하게 되므로 갱신도 허용한다.
        accessPolicy.requireSignedIn(member);
        RefreshToken stored = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!stored.isUsable(jwtProvider.hashToken(rawRefreshToken), SeoulDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return issueTokens(member);
    }

    /**
     * 로그아웃은 인증 여부와 무관하게 성공하는 멱등 동작이다.
     * 토큰이 없거나 만료·변조되었으면 폐기할 session이 없으므로 조용히 종료하고,
     * cookie 만료는 controller가 어떤 경우에도 응답에 담는다.
     */
    @Transactional
    public void logout(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return;
        }
        long memberId;
        try {
            memberId = jwtProvider.getMemberIdFromAccessToken(rawAccessToken);
        } catch (BusinessException exception) {
            return;
        }
        revokeSession(memberId);
    }

    /**
     * refresh token을 폐기하고 commit 이후 WebSocket session을 끊는다.
     * 이미 폐기된 상태에서 다시 호출해도 갱신 건수만 0이 되므로 멱등하다.
     * 진행 중인 매칭 pool/proposal/group은 정리하지 않는다. 로그아웃은 매칭 취소가 아니며,
     * 미응답은 기존 proposal timeout과 penalty 정책이 그대로 처리한다.
     * 회원 탈퇴(docs/19 4.4)에서도 같은 경로를 재사용한다.
     */
    @Transactional
    public void revokeSession(long memberId) {
        refreshTokenRepository.revokeByMemberId(memberId, SeoulDateTime.now());
        events.publishEvent(new MemberLoggedOutEvent(memberId));
    }

    private AuthTokenResponse issueTokens(Member member) {

        String accessToken = jwtProvider.createAccessToken(member);
        String refreshToken = jwtProvider.createRefreshToken(member);
        String refreshTokenHash = jwtProvider.hashToken(refreshToken);
        OffsetDateTime refreshTokenExpiresAt = SeoulDateTime.now()
                .plusSeconds(jwtProvider.getRefreshTokenExpiresInSeconds());

        RefreshToken storedRefreshToken = refreshTokenRepository.findByMemberId(member.getId())
                .map(existingToken -> {
                    existingToken.rotate(refreshTokenHash, refreshTokenExpiresAt);
                    return existingToken;
                })
                .orElseGet(() -> RefreshToken.issue(member, refreshTokenHash, refreshTokenExpiresAt));
        refreshTokenRepository.save(storedRefreshToken);

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
                    // 탈퇴 회원 판정을 프로필 갱신보다 먼저 끝낸다. 순서가 바뀌면 재가입이
                    // 거부된 회원의 익명화된 프로필이 OAuth 응답으로 다시 채워진다.
                    rejoinPolicy.rejoinIfWithdrawn(member);
                    member.restoreExpiredSuspension(SeoulDateTime.now());
                    // 정지 회원은 로그인해서 조회는 할 수 있어야 한다. 영구 제한만 막는다.
                    accessPolicy.requireSignedIn(member);
                    member.updateNaverProfile(naverUser.email(), naverUser.nickname(), naverUser.profileImageUrl());
                    return member;
                })
                .orElseGet(() -> memberRepository.save(Member.createNaverMember(
                        naverUser.providerUserId(),
                        naverUser.email(),
                        naverUser.nickname(),
                        naverUser.profileImageUrl()
                )));
    }

    private Member upsertKakaoMember(KakaoUserResponse kakaoUser) {
        return memberRepository.findByProviderAndProviderUserId(Member.PROVIDER_KAKAO, kakaoUser.providerUserId())
                .map(member -> {
                    // 탈퇴 회원 판정을 프로필 갱신보다 먼저 끝낸다. 순서가 바뀌면 재가입이
                    // 거부된 회원의 익명화된 프로필이 OAuth 응답으로 다시 채워진다.
                    rejoinPolicy.rejoinIfWithdrawn(member);
                    member.restoreExpiredSuspension(SeoulDateTime.now());
                    // 정지 회원은 로그인해서 조회는 할 수 있어야 한다. 영구 제한만 막는다.
                    accessPolicy.requireSignedIn(member);
                    member.updateKakaoProfile(kakaoUser.email(), kakaoUser.nickname(), kakaoUser.profileImageUrl());
                    return member;
                })
                .orElseGet(() -> memberRepository.save(Member.createKakaoMember(
                        kakaoUser.providerUserId(),
                        kakaoUser.email(),
                        kakaoUser.nickname(),
                        kakaoUser.profileImageUrl()
                )));
    }
}
