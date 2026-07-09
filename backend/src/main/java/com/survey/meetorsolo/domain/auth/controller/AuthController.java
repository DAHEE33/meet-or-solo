package com.survey.meetorsolo.domain.auth.controller;

import com.survey.meetorsolo.domain.auth.dto.AuthTokenResponse;
import com.survey.meetorsolo.domain.auth.service.AuthService;
import com.survey.meetorsolo.global.response.ApiResponse;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/api/auth/kakao/login")
    public ResponseEntity<Void> kakaoLogin() {
        URI authorizeUri = authService.getKakaoAuthorizeUri();
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizeUri.toString())
                .build();
    }

    @GetMapping("/api/auth/kakao/callback")
    public ApiResponse<AuthTokenResponse> kakaoCallback(@RequestParam String code) {
        return ApiResponse.success(authService.loginWithKakao(code));
    }
}
