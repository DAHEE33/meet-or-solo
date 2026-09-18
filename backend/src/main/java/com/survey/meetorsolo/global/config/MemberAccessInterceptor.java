package com.survey.meetorsolo.global.config;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import com.survey.meetorsolo.domain.member.service.SuspendedActivityPolicy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 제재 상태를 요청 진입 지점에서 검사한다.
 *
 * <p>정지({@code SUSPENDED})는 조회를 막지 않으므로 요청이 활동인지에 따라 판정이 갈린다.
 * 활동 여부는 {@link SuspendedActivityPolicy}의 목록으로 정한다. 영구 제한은 두 경로 모두
 * 차단된다.
 */
@Component
public class MemberAccessInterceptor implements HandlerInterceptor {

    private final JwtProvider jwtProvider;
    private final MemberAccessPolicy accessPolicy;
    private final SuspendedActivityPolicy activityPolicy;

    public MemberAccessInterceptor(
            JwtProvider jwtProvider,
            ObjectProvider<MemberAccessPolicy> accessPolicy,
            ObjectProvider<SuspendedActivityPolicy> activityPolicy
    ) {
        this.jwtProvider = jwtProvider;
        this.accessPolicy = accessPolicy.getIfAvailable();
        // @WebMvcTest slice에는 domain bean이 없다. 의존성이 없는 정책이라 새로 만들어 쓴다.
        this.activityPolicy = activityPolicy.getIfAvailable(SuspendedActivityPolicy::new);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = cookie(request, "access_token");
        if (accessPolicy == null || token == null || token.isBlank()) {
            return true;
        }
        long memberId = jwtProvider.getMemberIdFromAccessToken(token);
        if (activityPolicy.isRestricted(request.getMethod(), request.getRequestURI())) {
            accessPolicy.requireAccessible(memberId);
        } else {
            accessPolicy.requireBrowsable(memberId);
        }
        return true;
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
