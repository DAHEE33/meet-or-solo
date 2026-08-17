package com.survey.meetorsolo.domain.admin.member.service;

import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public record AdminMemberFilter(String query, AdminMemberStatus status, String role) {

    public String fingerprint() {
        String canonical = value(query) + "|" + value(status) + "|" + value(role);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }
}
