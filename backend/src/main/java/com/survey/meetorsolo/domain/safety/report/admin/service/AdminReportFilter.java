package com.survey.meetorsolo.domain.safety.report.admin.service;

import com.survey.meetorsolo.domain.safety.report.admin.dto.AdminReportStatus;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;

public record AdminReportFilter(
        AdminReportStatus status,
        MatchReportReasonCode reason,
        OffsetDateTime createdFrom,
        OffsetDateTime createdTo
) {
    public String fingerprint() {
        String canonical = value(status) + "|" + value(reason) + "|"
                + value(createdFrom) + "|" + value(createdTo);
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
