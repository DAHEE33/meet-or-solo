package com.survey.meetorsolo.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.domain.member.service.SuspendedActivityPolicy;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 요청이 활동인지 조회인지에 따라 판정이 갈리는지 검증한다.
 *
 * <p>여기서 판정을 잘못 고르면 정지 회원이 활동을 하거나(차단 실패) 아무것도 못 보게 된다
 * (과잉 차단). 두 방향 모두 확인한다.
 */
class MemberAccessInterceptorTest {

    private static final long MEMBER_ID = 27L;
    private static final String TOKEN = "access-token";

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final MemberAccessPolicy accessPolicy = mock(MemberAccessPolicy.class);
    private final MemberAccessInterceptor interceptor = new MemberAccessInterceptor(
            jwtProvider, provider(accessPolicy), provider(new SuspendedActivityPolicy()));

    @Test
    void 활동_요청은_정지를_차단하는_판정을_쓴다() {
        when(jwtProvider.getMemberIdFromAccessToken(TOKEN)).thenReturn(MEMBER_ID);

        assertThat(interceptor.preHandle(
                request("POST", "/api/festivals/144/checkin"), new MockHttpServletResponse(), new Object()))
                .isTrue();

        verify(accessPolicy).requireAccessible(MEMBER_ID);
        verify(accessPolicy, never()).requireBrowsable(MEMBER_ID);
    }

    @Test
    void 조회_요청은_정지를_통과시키는_판정을_쓴다() {
        when(jwtProvider.getMemberIdFromAccessToken(TOKEN)).thenReturn(MEMBER_ID);

        assertThat(interceptor.preHandle(
                request("GET", "/api/festivals/144"), new MockHttpServletResponse(), new Object()))
                .isTrue();

        verify(accessPolicy).requireBrowsable(MEMBER_ID);
        verify(accessPolicy, never()).requireAccessible(MEMBER_ID);
    }

    @Test
    void 목록에_없는_상태_변경은_조회_판정을_쓴다() {
        // 차단 목록 방식이므로 등재되지 않은 쓰기는 통과한다. 분류 누락은
        // SuspendedActivityPolicyCoverageTest가 잡는다.
        when(jwtProvider.getMemberIdFromAccessToken(TOKEN)).thenReturn(MEMBER_ID);

        interceptor.preHandle(
                request("POST", "/api/match-groups/31/reports"), new MockHttpServletResponse(), new Object());

        verify(accessPolicy).requireBrowsable(MEMBER_ID);
        verify(accessPolicy, never()).requireAccessible(MEMBER_ID);
    }

    @Test
    void cookie가_없으면_아무_판정도_하지_않는다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/festivals/144/checkin");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        verifyNoInteractions(accessPolicy);
        verifyNoInteractions(jwtProvider);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setCookies(new Cookie("access_token", TOKEN));
        return request;
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }
}
