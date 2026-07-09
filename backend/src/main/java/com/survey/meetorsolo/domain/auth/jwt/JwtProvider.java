package com.survey.meetorsolo.domain.auth.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final Duration accessTokenDuration;
    private final Duration refreshTokenDuration;

    public JwtProvider(
            ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expires-minutes}") long accessTokenExpiresMinutes,
            @Value("${app.jwt.refresh-token-expires-days}") long refreshTokenExpiresDays
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenDuration = Duration.ofMinutes(accessTokenExpiresMinutes);
        this.refreshTokenDuration = Duration.ofDays(refreshTokenExpiresDays);
    }

    public String createAccessToken(Member member) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(member.getId()));
        claims.put("typ", "access");
        claims.put("provider", member.getProvider());
        claims.put("role", member.getRole());
        claims.put("status", member.getStatus());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(accessTokenDuration).getEpochSecond());
        return createToken(claims);
    }

    public String createRefreshToken(Member member) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(member.getId()));
        claims.put("typ", "refresh");
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(refreshTokenDuration).getEpochSecond());
        return createToken(claims);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return BASE64_URL_ENCODER.encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenDuration.toSeconds();
    }

    public long getRefreshTokenExpiresInSeconds() {
        return refreshTokenDuration.toSeconds();
    }

    private String createToken(Map<String, Object> claims) {
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(claims);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = sign(signingInput);
        return signingInput + "." + signature;
    }

    private String encodeJson(Object value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String sign(String signingInput) {
        if (secret.length == 0) {
            throw new IllegalStateException("JWT_SECRET 환경변수가 필요합니다.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
