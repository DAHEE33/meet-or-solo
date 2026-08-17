package com.survey.meetorsolo.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final MemberAccessInterceptor memberAccessInterceptor;

    public WebMvcConfig(ObjectProvider<MemberAccessInterceptor> memberAccessInterceptor) {
        this.memberAccessInterceptor = memberAccessInterceptor.getIfAvailable();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (memberAccessInterceptor == null) return;
        registry.addInterceptor(memberAccessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/health");
    }
}
