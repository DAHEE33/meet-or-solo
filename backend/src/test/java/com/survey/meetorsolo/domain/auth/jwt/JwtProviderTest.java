package com.survey.meetorsolo.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            new ObjectMapper(),
            "test-jwt-secret-that-is-long-enough",
            30,
            14
    );

    @Test
    void accessToken에서_회원_ID를_검증한다() {
        String token = jwtProvider.createAccessToken(1L, "PROFILE_REQUIRED");

        assertThat(jwtProvider.getMemberIdFromAccessToken(token)).isEqualTo(1L);
    }

    @Test
    void 변조된_accessToken을_거부한다() {
        String token = jwtProvider.createAccessToken(1L, "PROFILE_REQUIRED") + "changed";

        assertThatThrownBy(() -> jwtProvider.getMemberIdFromAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
