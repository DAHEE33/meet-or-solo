package com.survey.meetorsolo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/kakao/**", "/api/auth/naver/**",
                                "/api/auth/admin/**", "/api/health").permitAll()
                        .anyRequest().permitAll()
                )
                .build();
    }

    /**
     * 슈퍼관리자 ID/PW 비밀번호 해시({@code docs/30}).
     *
     * <p>BCrypt는 salt를 해시 문자열 안에 담고 비교 비용이 상수에 가깝다. 강도는 기본값
     * (10)을 쓴다 — 로그인 빈도가 낮아 올릴 실익이 적고, 올리면 기존 해시를 전부 다시
     * 만들어야 한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
