package com.survey.meetorsolo.external.naver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NaverTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") String expiresIn,
        String error,
        @JsonProperty("error_description") String errorDescription
) {
}
