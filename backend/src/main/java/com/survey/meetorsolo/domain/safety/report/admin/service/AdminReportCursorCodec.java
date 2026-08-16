package com.survey.meetorsolo.domain.safety.report.admin.service;

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

@Component
public class AdminReportCursorCodec {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private final byte[] secret;

    public AdminReportCursorCodec(
            @Value("${app.admin.report.cursor-hmac-secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_REPORT_CURSOR_HMAC_SECRET 환경변수가 필요합니다.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "ADMIN_REPORT_CURSOR_HMAC_SECRET은 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        this.secret = secretBytes;
    }

    public String encode(OffsetDateTime createdAt, long reportId, String filterFingerprint) {
        String payload = createdAt.toInstant().getEpochSecond() + ":"
                + createdAt.getNano() + ":" + reportId + ":" + filterFingerprint;
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + encode(sign(encodedPayload));
    }

    public Cursor decode(String cursor, String expectedFilterFingerprint) {
        try {
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw invalidCursor();
            }
            if (!MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) {
                throw invalidCursor();
            }
            String[] values = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
                    .split(":", -1);
            if (values.length != 4 || !expectedFilterFingerprint.equals(values[3])) {
                throw invalidCursor();
            }
            long epochSecond = Long.parseLong(values[0]);
            int nano = Integer.parseInt(values[1]);
            long reportId = Long.parseLong(values[2]);
            if (nano < 0 || nano > 999_999_999 || reportId <= 0) {
                throw invalidCursor();
            }
            return new Cursor(OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nano), ZoneOffset.UTC), reportId);
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
            throw new IllegalStateException("관리자 신고 cursor 서명에 실패했습니다.", exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.ADMIN_REPORT_INVALID_REQUEST, "cursor 값이 올바르지 않습니다.");
    }

    public record Cursor(OffsetDateTime createdAt, long reportId) {
    }
}
