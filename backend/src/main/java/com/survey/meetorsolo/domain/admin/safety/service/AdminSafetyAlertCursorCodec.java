package com.survey.meetorsolo.domain.admin.safety.service;

import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 관리자 안전 알림 목록 cursor codec.
 *
 * <p>{@code AdminMemberCursorCodec}과 같이 관리자 전용 HMAC Secret을 공유하고 payload
 * prefix로 도메인을 분리한다. JWT Secret과는 분리된 키다.
 */
@Component
public class AdminSafetyAlertCursorCodec {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String PAYLOAD_PREFIX = "safety-alert:v1:";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final byte[] secret;

    public AdminSafetyAlertCursorCodec(
            @Value("${app.admin.report.cursor-hmac-secret}") String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "관리자 cursor HMAC Secret은 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        this.secret = bytes;
    }

    public String encode(OffsetDateTime createdAt, long alertId, String fingerprint) {
        String payload = PAYLOAD_PREFIX + createdAt.toInstant().getEpochSecond() + ":"
                + createdAt.getNano() + ":" + alertId + ":" + fingerprint;
        String encoded = encode(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + encode(sign(encoded));
    }

    public Cursor decode(String value, String expectedFingerprint) {
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw invalidCursor();
            }
            if (!MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) {
                throw invalidCursor();
            }
            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (!payload.startsWith(PAYLOAD_PREFIX)) {
                throw invalidCursor();
            }
            String[] values = payload.substring(PAYLOAD_PREFIX.length()).split(":", -1);
            if (values.length != 4 || !expectedFingerprint.equals(values[3])) {
                throw invalidCursor();
            }
            long epochSecond = Long.parseLong(values[0]);
            int nano = Integer.parseInt(values[1]);
            long alertId = Long.parseLong(values[2]);
            if (nano < 0 || nano > 999_999_999 || alertId <= 0) {
                throw invalidCursor();
            }
            return new Cursor(
                    OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nano), ZoneOffset.UTC),
                    alertId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidCursor();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("관리자 안전 알림 cursor 서명에 실패했습니다.", exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private BusinessException invalidCursor() {
        return new BusinessException(
                ErrorCode.ADMIN_SAFETY_ALERT_INVALID_REQUEST, "cursor 값이 올바르지 않습니다.");
    }

    public record Cursor(OffsetDateTime createdAt, long alertId) {
    }
}
