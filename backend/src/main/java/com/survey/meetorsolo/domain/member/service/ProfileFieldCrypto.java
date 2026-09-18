package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileFieldCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public ProfileFieldCrypto(@Value("${app.profile.encryption-key:}") String encodedKey) {
        this.key = validateAndCreateKey(encodedKey);
    }

    public byte[] encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    public String decrypt(byte[] value) {
        if (value == null) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private SecretKeySpec validateAndCreateKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "PROFILE_ENCRYPTION_KEY is required and must be a Base64-encoded 32-byte AES-256 key."
            );
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "PROFILE_ENCRYPTION_KEY must be valid standard Base64 encoding.",
                    exception
            );
        }

        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "PROFILE_ENCRYPTION_KEY must decode to exactly 32 bytes for AES-256; decoded length="
                            + decoded.length
            );
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
