package com.survey.meetorsolo.domain.member.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberConsentQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemberConsentQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasAgreedConsent(Long memberId, String consentType) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM member_consents
                    WHERE member_id = ? AND consent_type = ? AND agreed = TRUE AND revoked_at IS NULL
                )
                """;
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(sql, Boolean.class, memberId, consentType));
    }
}
