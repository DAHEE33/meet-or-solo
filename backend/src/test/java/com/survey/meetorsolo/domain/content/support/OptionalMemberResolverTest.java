package com.survey.meetorsolo.domain.content.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 2.1 회귀 방지.
 *
 * <p>이 해석기가 예외를 흘리면 공개 화면(축제/관광지 상세)이 {@code 401}을 받고 frontend
 * {@code apiClient}가 비로그인 방문자를 통째로 {@code /login}으로 보낸다. 그래서 "절대 던지지
 * 않는다"가 이 클래스의 핵심 계약이다.
 */
class OptionalMemberResolverTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final OptionalMemberResolver resolver = new OptionalMemberResolver(jwtProvider);

    @Test
    void 쿠키가_없으면_토큰을_해석하지_않고_null이다() {
        assertThat(resolver.resolveOrNull(null)).isNull();
        verifyNoInteractions(jwtProvider);
    }

    @Test
    void 빈_문자열과_공백_쿠키도_null이다() {
        assertThat(resolver.resolveOrNull("")).isNull();
        assertThat(resolver.resolveOrNull("   ")).isNull();
        verifyNoInteractions(jwtProvider);
    }

    @Test
    void 유효한_토큰이면_회원_id를_반환한다() {
        when(jwtProvider.getMemberIdFromAccessToken("valid")).thenReturn(9_110_001L);

        assertThat(resolver.resolveOrNull("valid")).isEqualTo(9_110_001L);
    }

    @Test
    void 만료되거나_위조된_토큰은_예외를_흘리지_않고_null이다() {
        when(jwtProvider.getMemberIdFromAccessToken(anyString()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThat(resolver.resolveOrNull("expired-or-tampered")).isNull();
    }

    @Test
    void 토큰_해석이_다른_런타임_예외를_던져도_null이다() {
        when(jwtProvider.getMemberIdFromAccessToken(anyString()))
                .thenThrow(new IllegalArgumentException("Invalid JWT format"));

        assertThat(resolver.resolveOrNull("garbage")).isNull();
    }
}
