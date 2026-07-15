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
import java.util.UUID;
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
            @Value("${app.jwt.refresh-token-expires-minutes}") long refreshTokenExpiresMinutes
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenDuration = Duration.ofMinutes(accessTokenExpiresMinutes);
        this.refreshTokenDuration = Duration.ofMinutes(refreshTokenExpiresMinutes);
    }

    public String createAccessToken(Member member) {
        return createAccessToken(member.getId(), member.getProvider(), member.getRole(), member.getStatus());
    }

    public String createAccessToken(Long memberId, String status) {
        return createAccessToken(memberId, Member.PROVIDER_KAKAO, Member.ROLE_USER, status);
    }

    private String createAccessToken(Long memberId, String provider, String role, String status) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(memberId));
        claims.put("typ", "access");
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("provider", provider);
        claims.put("role", role);
        claims.put("status", status);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(accessTokenDuration).getEpochSecond());
        return createToken(claims);
    }

    public String createRefreshToken(Member member) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(member.getId()));
        claims.put("typ", "refresh");
        claims.put("jti", UUID.randomUUID().toString());
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

    public Long getMemberIdFromAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            String signingInput = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(
                    sign(signingInput).getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    Map.class
            );
            if (!"access".equals(claims.get("typ"))) {
                throw new IllegalArgumentException("Invalid token type");
            }
            Object expiresAt = claims.get("exp");
            if (!(expiresAt instanceof Number) || ((Number) expiresAt).longValue() <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Expired JWT");
            }
            return Long.valueOf(String.valueOf(claims.get("sub")));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
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
