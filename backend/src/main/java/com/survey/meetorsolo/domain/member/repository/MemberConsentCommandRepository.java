package com.survey.meetorsolo.domain.member.repository;

import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * `member_consents` 쓰기 전용 repository.
 *
 * <p>조회는 {@link MemberConsentQueryRepository}가 담당한다.
 */
@Repository
public class MemberConsentCommandRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemberConsentCommandRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 동의를 기록한다. 같은 (회원, 유형, 버전) 기록이 이미 있으면 갱신한다.
     *
     * <p>`uq_member_consents_member_type_version` 때문에 "철회 후 재동의"는 새 row가 아니라
     * 기존 row 갱신이다. 조회 후 분기하면 중복 제출 시 UNIQUE 위반이 날 수 있어
     * `ON CONFLICT`로 한 문장에서 원자적으로 처리한다.
     */
    public void agree(Long memberId, MemberConsentType type, OffsetDateTime agreedAt) {
        String sql = """
                INSERT INTO member_consents (member_id, consent_type, version, agreed, agreed_at)
                VALUES (?, ?, ?, TRUE, ?)
                ON CONFLICT (member_id, consent_type, version)
                DO UPDATE SET
                    agreed = TRUE,
                    agreed_at = EXCLUDED.agreed_at,
                    revoked_at = NULL
                """;
        jdbcTemplate.update(sql, memberId, type.name(), type.currentVersion(), agreedAt);
    }

    /**
     * 유효한 동의를 철회한다. 철회 대상이 없으면 아무것도 바꾸지 않고 false를 반환한다.
     *
     * <p>`agreed`는 FALSE로 바꾸지 않는다. "동의한 적이 있다"는 사실 자체가 감사 기록이고,
     * `agreed = FALSE`는 "처음부터 거부"라는 다른 상태를 위해 남겨둔다. 버전을 지정하지 않고
     * 해당 유형의 유효한 동의를 모두 철회한다.
     */
    public boolean revoke(Long memberId, MemberConsentType type, OffsetDateTime revokedAt) {
        String sql = """
                UPDATE member_consents
                SET revoked_at = ?
                WHERE member_id = ? AND consent_type = ? AND agreed = TRUE AND revoked_at IS NULL
                """;
        return jdbcTemplate.update(sql, revokedAt, memberId, type.name()) > 0;
    }
}
