package com.survey.meetorsolo.domain.content.support;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import org.springframework.stereotype.Component;

/**
 * 공개 조회 API에서 "로그인했으면 누구인지, 아니면 비로그인"을 해석한다.
 *
 * <p>기존 controller들이 복사해 쓰는 {@code memberId(accessToken)} helper는 쿠키가 없으면
 * {@code UNAUTHORIZED}를 던지지만, 이 해석기는 <b>절대 던지지 않고 {@code null}을 반환한다.</b>
 *
 * <p>frontend {@code apiClient}가 모든 {@code 401}을 {@code /login} 전역 리다이렉트로 처리하기
 * 때문이다. 공개 화면에 붙는 댓글 목록·engagement 조회가 {@code 401}을 내면 축제 상세를 열기만
 * 해도 로그인 화면으로 튕긴다. 그래서 쿠키가 없는 경우뿐 아니라 <b>만료·위조된 토큰도 비로그인으로
 * 취급한다</b>(docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.1).
 *
 * <p>감수하는 비용은 쿠키가 만료된 사용자가 자신의 찜 상태를 잠시 보지 못하는 것이고, 이는 화면
 * 전체가 로그인으로 튕기는 것보다 낫다.
 */
@Component
public class OptionalMemberResolver {

    private final JwtProvider jwtProvider;

    public OptionalMemberResolver(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * @param accessToken {@code access_token} 쿠키 값. 없으면 {@code null}이 들어온다.
     * @return 유효한 access token의 회원 id, 그 외에는 모두 {@code null}
     */
    public Long resolveOrNull(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            return jwtProvider.getMemberIdFromAccessToken(accessToken);
        } catch (RuntimeException exception) {
            // JwtProvider는 만료·서명 불일치·타입 불일치를 BusinessException(UNAUTHORIZED)으로
            // 바꿔 던진다. 공개 조회에서는 그 예외가 화면 전체를 로그인으로 튕기게 만들므로
            // 여기서 삼키고 비로그인으로 본다.
            return null;
        }
    }
}
