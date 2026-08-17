package com.survey.meetorsolo.global.config;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.service.MemberAccessPolicy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class MemberAccessInterceptor implements HandlerInterceptor {

    private final JwtProvider jwtProvider;
    private final MemberAccessPolicy accessPolicy;

    public MemberAccessInterceptor(
            JwtProvider jwtProvider, ObjectProvider<MemberAccessPolicy> accessPolicy) {
        this.jwtProvider = jwtProvider;
        this.accessPolicy = accessPolicy.getIfAvailable();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = cookie(request, "access_token");
        if (accessPolicy != null && token != null && !token.isBlank()) {
            accessPolicy.requireAccessible(jwtProvider.getMemberIdFromAccessToken(token));
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
