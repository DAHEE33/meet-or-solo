package com.survey.meetorsolo.domain.admin.member.service;

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
public class AdminMemberCursorCodec {

    private final byte[] secret;

    public AdminMemberCursorCodec(@Value("${app.admin.report.cursor-hmac-secret}") String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("관리자 cursor HMAC Secret은 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        this.secret = bytes;
    }

    public String encode(OffsetDateTime createdAt, long memberId, String fingerprint) {
        String payload = "member:v1:" + createdAt.toInstant().getEpochSecond() + ":"
                + createdAt.getNano() + ":" + memberId + ":" + fingerprint;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
    }

    public Cursor decode(String value, String expectedFingerprint) {
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) {
                throw invalid();
            }
            String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
                    .split(":", -1);
            if (fields.length != 6 || !"member".equals(fields[0]) || !"v1".equals(fields[1])
                    || !expectedFingerprint.equals(fields[5])) {
                throw invalid();
            }
            int nano = Integer.parseInt(fields[3]);
            long memberId = Long.parseLong(fields[4]);
            if (nano < 0 || nano > 999_999_999 || memberId <= 0) throw invalid();
            return new Cursor(OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(Long.parseLong(fields[2]), nano), ZoneOffset.UTC), memberId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("관리자 회원 cursor 서명에 실패했습니다.", exception);
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.ADMIN_MEMBER_INVALID_REQUEST, "cursor 값이 올바르지 않습니다.");
    }

    public record Cursor(OffsetDateTime createdAt, long memberId) {
    }
}
