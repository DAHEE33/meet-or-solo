package com.survey.meetorsolo.domain.member.repository;

import com.survey.meetorsolo.domain.member.dto.MemberConsentResponse;
import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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

    /**
     * 지정한 유형들의 현재 동의 상태를 조회한다.
     *
     * <p>기록이 없는 유형은 결과에 포함되지 않는다. "기록 없음"을 `agreed = false` 항목으로
     * 채우는 것은 service의 역할이다.
     *
     * <p>같은 유형에 여러 버전이 쌓여 있을 수 있으므로 유효한 동의(`agreed = TRUE`,
     * `revoked_at IS NULL`)를 우선 고르고, 그다음 최근 기록을 고른다.
     */
    public List<MemberConsentResponse> findLatestByMemberIdAndTypes(
            Long memberId, Collection<MemberConsentType> types) {
        if (types.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", types.stream().map(type -> "?").toList());
        String sql = """
                SELECT DISTINCT ON (consent_type)
                    consent_type, agreed, version, agreed_at, revoked_at
                FROM member_consents
                WHERE member_id = ? AND consent_type IN (%s)
                ORDER BY consent_type,
                    (agreed = TRUE AND revoked_at IS NULL) DESC,
                    COALESCE(agreed_at, created_at) DESC,
                    id DESC
                """.formatted(placeholders);

        Object[] args = new Object[types.size() + 1];
        args[0] = memberId;
        int index = 1;
        for (MemberConsentType type : types) {
            args[index++] = type.name();
        }

        return jdbcTemplate.query(sql, (rs, rowNum) -> new MemberConsentResponse(
                rs.getString("consent_type"),
                rs.getBoolean("agreed") && rs.getObject("revoked_at") == null,
                rs.getString("version"),
                rs.getObject("agreed_at", OffsetDateTime.class),
                rs.getObject("revoked_at", OffsetDateTime.class)
        ), args);
    }
}
