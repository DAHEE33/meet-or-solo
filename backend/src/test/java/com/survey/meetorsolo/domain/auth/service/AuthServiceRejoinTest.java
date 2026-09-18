package com.survey.meetorsolo.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.repository.RefreshTokenRepository;
import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.domain.member.service.MemberRejoinPolicy;
import com.survey.meetorsolo.domain.member.service.MemberSanctionException;
import com.survey.meetorsolo.external.kakao.KakaoOAuthClient;
import com.survey.meetorsolo.external.naver.NaverOAuthClient;
import com.survey.meetorsolo.external.naver.dto.NaverTokenResponse;
import com.survey.meetorsolo.external.naver.dto.NaverUserResponse;
import com.survey.meetorsolo.global.error.ErrorCode;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/**
 * OAuth 로그인 경로의 탈퇴 회원 처리 순서({@code docs/19} 4.4).
 *
 * <p><b>이 테스트가 지키는 불변식</b>: 탈퇴 회원 판정이 프로필 갱신보다 먼저 끝나야 한다.
 * 순서가 바뀌면 재가입이 거부된 회원의 익명화된 닉네임·이메일·프로필 이미지가 OAuth 응답으로
 * 다시 채워진다. 개인정보 삭제 요구를 정면으로 위반하는 회귀인데, 코드만 읽으면 두 줄의
 * 순서 문제로 보여 리뷰에서 놓치기 쉽다.
 *
 * <p>Naver 경로로 검증한다. Kakao도 {@code upsertKakaoMember}에 같은 순서로 들어가 있고
 * 구조가 동일하다.
 */
class AuthServiceRejoinTest {

    private static final String PROVIDER_USER_ID = "naver-rejoin-1";
    private static final OffsetDateTime WITHDRAWN_AT = OffsetDateTime.parse("2026-09-01T12:00:00+09:00");

    private final KakaoOAuthClient kakaoClient = mock(KakaoOAuthClient.class);
    private final NaverOAuthClient naverClient = mock(NaverOAuthClient.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final MemberAccessPolicy accessPolicy = mock(MemberAccessPolicy.class);
    private final MemberRejoinPolicy rejoinPolicy = mock(MemberRejoinPolicy.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuthService authService = new AuthService(
            kakaoClient, naverClient, memberRepository, refreshTokenRepository, jwtProvider,
            accessPolicy, rejoinPolicy, events);

    @Test
    void 재가입이_거부되면_익명화된_프로필이_OAuth_응답으로_되살아나지_않는다() {
        Member withdrawn = withdrawnMember();
        stubNaverLogin(withdrawn);
        when(rejoinPolicy.rejoinIfWithdrawn(withdrawn)).thenThrow(new MemberSanctionException(
                ErrorCode.MEMBER_REJOIN_BLOCKED, 1L,
                MemberSanctionNotice.forWithdrawn(WITHDRAWN_AT.plusDays(7), null)));

        assertThatThrownBy(() -> authService.loginWithNaver("code", "state"))
                .isInstanceOfSatisfying(MemberSanctionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_REJOIN_BLOCKED));

        // 판정이 프로필 갱신보다 먼저 끝났으므로 익명화된 값이 그대로 남아 있어야 한다.
        assertThat(withdrawn.getNickname()).isNull();
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getProfileImageUrl()).isNull();
        assertThat(withdrawn.getStatus()).isEqualTo(Member.STATUS_WITHDRAWN);
        // 거부된 회원에게 session을 발급해서는 안 된다.
        verify(refreshTokenRepository, never()).save(any());
    }

    /**
     * 판정이 접근 허용 검사보다도 먼저여야 한다.
     * {@code WITHDRAWN}은 {@code requireSignedIn}에서 {@code MEMBER_INACTIVE}로 먼저 막히므로,
     * 순서가 뒤면 쿨오프가 지난 회원도 영원히 로그인할 수 없다.
     */
    @Test
    void 탈퇴_판정은_접근_허용_검사보다_먼저_실행된다() {
        Member withdrawn = withdrawnMember();
        stubNaverLogin(withdrawn);
        stubRejoinSucceeds(withdrawn);

        authService.loginWithNaver("code", "state");

        InOrder order = inOrder(rejoinPolicy, accessPolicy);
        order.verify(rejoinPolicy).rejoinIfWithdrawn(withdrawn);
        order.verify(accessPolicy).requireSignedIn(withdrawn);
    }

    @Test
    void 쿨오프가_지난_탈퇴_회원은_프로필_재입력_상태로_부활하고_프로필이_다시_채워진다() {
        Member withdrawn = withdrawnMember();
        stubNaverLogin(withdrawn);
        stubRejoinSucceeds(withdrawn);

        authService.loginWithNaver("code", "state");

        assertThat(withdrawn.getStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
        // 재가입은 새 동의 아래 새 개인정보를 받는 것이므로 OAuth 프로필이 채워지는 게 맞다.
        // 탈퇴가 닉네임을 지웠으므로(nickname IS NULL) updateSocialProfile의 "비어 있으면
        // 채운다" 조건에 걸린다.
        assertThat(withdrawn.getNickname()).isEqualTo("돌아온닉네임");
        assertThat(withdrawn.getEmail()).isEqualTo("rejoin@example.test");
        assertThat(withdrawn.getWithdrawnAt()).isNull();
    }

    /**
     * 정지 중 탈퇴한 회원이 재가입하면 상태가 SUSPENDED로 부활한다. 이때도 닉네임이
     * OAuth 값으로 채워져야 한다. {@code PROFILE_REQUIRED}가 아니어서 status 조건으로는
     * 걸리지 않고, 닉네임이 비어 있다는 조건으로만 채워지는 경로다. 비어 있는 채로 남으면
     * 살아 있는 계정이 다른 사용자에게 이름 없이 보인다.
     */
    @Test
    void 정지를_이어받아_부활해도_닉네임은_OAuth_값으로_채워진다() {
        Member member = suspendedWithdrawnMember();
        stubNaverLogin(member);
        when(rejoinPolicy.rejoinIfWithdrawn(member)).thenAnswer(invocation -> {
            member.rejoin(WITHDRAWN_AT.plusDays(7));
            return true;
        });

        authService.loginWithNaver("code", "state");

        assertThat(member.getStatus()).isEqualTo(Member.STATUS_SUSPENDED);
        assertThat(member.getNickname()).isEqualTo("돌아온닉네임");
    }

    /** 탈퇴 회원이 아니면 판정이 아무것도 바꾸지 않아야 한다. 일반 로그인 회귀 방지. */
    @Test
    void 탈퇴_회원이_아니면_기존_로그인_흐름이_그대로다() {
        Member active = Member.createNaverMember(PROVIDER_USER_ID, "user@example.test", "닉네임", null);
        active.completeProfile("닉네임", "user@example.test", null, new byte[]{1}, new byte[]{2});
        stubNaverLogin(active);
        when(rejoinPolicy.rejoinIfWithdrawn(active)).thenReturn(false);

        authService.loginWithNaver("code", "state");

        assertThat(active.getStatus()).isEqualTo(Member.STATUS_ACTIVE);
        // 사용자가 직접 고친 닉네임을 OAuth 값으로 덮지 않는다. updateSocialProfile은
        // status가 PROFILE_REQUIRED이거나 닉네임이 비어 있을 때만 반영한다.
        assertThat(active.getNickname()).isEqualTo("닉네임");
        verify(rejoinPolicy).rejoinIfWithdrawn(active);
    }

    /**
     * OAuth가 닉네임을 주지 않는 경우. 카카오는 닉네임 제공이 선택 동의라 실제로 비어 올 수 있다.
     *
     * <p>이전 구현은 익명화 문구를 컬럼에 저장하고 그 문구를 sentinel로 썼기 때문에, 여기서
     * 채울 값이 없으면 <b>살아 있는 계정에 '탈퇴한 회원'이 그대로 남았다.</b> 지금은 탈퇴가
     * 닉네임을 지우므로 최악의 경우도 빈 닉네임이고, 정지 중에도 열리는 프로필 수정 화면에서
     * 본인이 채울 수 있다.
     */
    @Test
    void OAuth가_닉네임을_주지_않아도_표시_문구가_계정에_남지_않는다() {
        Member withdrawn = withdrawnMember();
        stubNaverLoginWithoutNickname(withdrawn);
        stubRejoinSucceeds(withdrawn);

        authService.loginWithNaver("code", "state");

        assertThat(withdrawn.getStatus()).isEqualTo(Member.STATUS_PROFILE_REQUIRED);
        assertThat(withdrawn.getNickname()).isNotEqualTo(Member.WITHDRAWN_NICKNAME);
        assertThat(withdrawn.getNickname()).isNull();
    }

    private void stubRejoinSucceeds(Member member) {
        when(rejoinPolicy.rejoinIfWithdrawn(member)).thenAnswer(invocation -> {
            member.rejoin(WITHDRAWN_AT.plusDays(7));
            return true;
        });
    }

    private void stubNaverLogin(Member existing) {
        when(naverClient.requestToken("code", "state"))
                .thenReturn(new NaverTokenResponse("naver-access", "bearer", "3600", null, null));
        when(naverClient.requestUserInfo("naver-access")).thenReturn(new NaverUserResponse(
                "00", "success", new NaverUserResponse.Profile(
                        PROVIDER_USER_ID, "rejoin@example.test", "돌아온닉네임",
                        "https://image.example.test/new.png", null, null, null, null)));
        when(memberRepository.findByProviderAndProviderUserId(Member.PROVIDER_NAVER, PROVIDER_USER_ID))
                .thenReturn(Optional.of(existing));
        when(jwtProvider.createAccessToken(any(Member.class))).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(any(Member.class))).thenReturn("refresh-token");
        when(jwtProvider.hashToken("refresh-token")).thenReturn("refresh-token-hash");
        when(jwtProvider.getAccessTokenExpiresInSeconds()).thenReturn(1800L);
        when(jwtProvider.getRefreshTokenExpiresInSeconds()).thenReturn(1209600L);
        when(refreshTokenRepository.findByMemberId(any())).thenReturn(Optional.empty());
    }

    private void stubNaverLoginWithoutNickname(Member existing) {
        when(naverClient.requestToken("code", "state"))
                .thenReturn(new NaverTokenResponse("naver-access", "bearer", "3600", null, null));
        when(naverClient.requestUserInfo("naver-access")).thenReturn(new NaverUserResponse(
                "00", "success", new NaverUserResponse.Profile(
                        PROVIDER_USER_ID, "rejoin@example.test", null,
                        null, null, null, null, null)));
        when(memberRepository.findByProviderAndProviderUserId(Member.PROVIDER_NAVER, PROVIDER_USER_ID))
                .thenReturn(Optional.of(existing));
        when(jwtProvider.createAccessToken(any(Member.class))).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(any(Member.class))).thenReturn("refresh-token");
        when(jwtProvider.hashToken("refresh-token")).thenReturn("refresh-token-hash");
        when(jwtProvider.getAccessTokenExpiresInSeconds()).thenReturn(1800L);
        when(jwtProvider.getRefreshTokenExpiresInSeconds()).thenReturn(1209600L);
        when(refreshTokenRepository.findByMemberId(any())).thenReturn(Optional.empty());
    }

    /** 정지 중 탈퇴해 잔여 기간이 남은 회원. 재가입하면 SUSPENDED로 부활한다. */
    private static Member suspendedWithdrawnMember() {
        Member member = Member.createNaverMember(
                PROVIDER_USER_ID, "old@example.test", "원래닉네임", "https://image.example.test/old.png");
        member.completeProfile("원래닉네임", "old@example.test", "소개글",
                new byte[]{1, 2}, new byte[]{3, 4});
        member.suspend(WITHDRAWN_AT.minusDays(1), WITHDRAWN_AT.plusDays(30), "HARASSMENT");
        member.withdraw(WITHDRAWN_AT, false, false);
        return member;
    }

    private static Member withdrawnMember() {
        Member member = Member.createNaverMember(
                PROVIDER_USER_ID, "old@example.test", "원래닉네임", "https://image.example.test/old.png");
        member.completeProfile("원래닉네임", "old@example.test", "소개글",
                new byte[]{1, 2}, new byte[]{3, 4});
        member.withdraw(WITHDRAWN_AT, false, false);
        return member;
    }
}
