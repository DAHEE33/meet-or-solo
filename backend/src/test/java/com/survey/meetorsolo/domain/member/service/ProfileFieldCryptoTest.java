package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class ProfileFieldCryptoTest {

    private final ProfileFieldCrypto crypto = new ProfileFieldCrypto(
            Base64.getEncoder().encodeToString(new byte[32])
    );

    @Test
    void 프로필_필드를_AES_GCM으로_암복호화한다() {
        byte[] encrypted = crypto.encrypt("20S");

        assertThat(encrypted).isNotEqualTo("20S".getBytes());
        assertThat(crypto.decrypt(encrypted)).isEqualTo("20S");
    }

    @Test
    void 암호화_키가_비어_있으면_생성_시점에_실패한다() {
        assertThatThrownBy(() -> new ProfileFieldCrypto(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROFILE_ENCRYPTION_KEY");
    }

    @Test
    void Base64_decode_결과가_32바이트가_아니면_생성_시점에_실패한다() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new ProfileFieldCrypto(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
    }
}
