package com.survey.meetorsolo.domain.auth.dto;

public record AuthTokenResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds,
        Long memberId,
        String memberStatus
) {
}
