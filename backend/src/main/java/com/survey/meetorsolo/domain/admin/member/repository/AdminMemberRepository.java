package com.survey.meetorsolo.domain.admin.member.repository;

import com.survey.meetorsolo.domain.admin.member.dto.*;
import com.survey.meetorsolo.domain.admin.member.service.AdminMemberCursorCodec.Cursor;
import com.survey.meetorsolo.domain.admin.member.service.AdminMemberFilter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminMemberRepository {

    private static final String MEMBER_COLUMNS = """
            SELECT m.id, m.nickname, m.profile_image_url, m.role, m.status,
                   m.penalty_score, m.manner_temperature, m.suspended_at,
                   m.suspended_until, m.created_at, m.last_login_at
            FROM members m
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminMemberRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminMemberListItemResponse> findPage(
            AdminMemberFilter filter, Cursor cursor, int fetchSize) {
        StringBuilder sql = new StringBuilder(MEMBER_COLUMNS).append(" WHERE 1=1");
        Map<String, Object> parameters = new HashMap<>();
        if (filter.query() != null) {
            sql.append(" AND m.nickname ILIKE :query ESCAPE '!'");
            parameters.put("query", "%" + escapeLike(filter.query()) + "%");
        }
        if (filter.status() != null) {
            sql.append(" AND m.status=:status");
            parameters.put("status", filter.status().name());
        }
        if (filter.role() != null) {
            sql.append(" AND m.role=:role");
            parameters.put("role", filter.role());
        }
        if (cursor != null) {
            sql.append(" AND (m.created_at < :createdAt OR (m.created_at=:createdAt AND m.id < :memberId))");
            parameters.put("createdAt", cursor.createdAt());
            parameters.put("memberId", cursor.memberId());
        }
        sql.append(" ORDER BY m.created_at DESC, m.id DESC LIMIT :fetchSize");
        parameters.put("fetchSize", fetchSize);
        return jdbc.query(sql.toString(), parameters, this::mapListItem);
    }

    public Optional<AdminMemberListItemResponse> findSummary(long memberId) {
        return jdbc.query(MEMBER_COLUMNS + " WHERE m.id=:memberId", Map.of("memberId", memberId),
                this::mapListItem).stream().findFirst();
    }

    public List<AdminMemberReportHistoryResponse> findReports(long memberId) {
        return jdbc.query("""
                SELECT id, reason_code, status, created_at, resolved_at
                FROM reports
                WHERE reported_member_id=:memberId
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """, Map.of("memberId", memberId), (rs, rowNum) -> new AdminMemberReportHistoryResponse(
                rs.getLong("id"), rs.getString("reason_code"), rs.getString("status"),
                dateTime(rs, "created_at"), dateTime(rs, "resolved_at")));
    }

    public List<AdminMemberActionHistoryResponse> findActions(long memberId) {
        return jdbc.query("""
                SELECT id, action_type, reason_code, reason, report_id, created_at
                FROM admin_actions
                WHERE target_member_id=:memberId
                  AND action_type IN ('WARNING', 'SUSPEND', 'BAN', 'UNBAN', 'UNSUSPEND')
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """, Map.of("memberId", memberId), (rs, rowNum) -> new AdminMemberActionHistoryResponse(
                rs.getLong("id"), AdminMemberActionType.valueOf(rs.getString("action_type")),
                AdminMemberActionReasonCode.valueOf(rs.getString("reason_code")),
                rs.getString("reason"), nullableLong(rs, "report_id"), dateTime(rs, "created_at")));
    }

    public boolean hasActiveMatching(long memberId) {
        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM match_pools
                    WHERE member_id=:memberId AND status IN ('WAITING', 'LOCKED', 'PROPOSED')
                    UNION ALL
                    SELECT 1 FROM match_proposals
                    WHERE member_id=:memberId AND status='SENT'
                    UNION ALL
                    SELECT 1 FROM match_group_members
                    WHERE member_id=:memberId
                      AND status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
                )
                """, Map.of("memberId", memberId), Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public Optional<LockedReport> findReportForUpdate(long reportId) {
        return jdbc.query("""
                SELECT id, reported_member_id, status
                FROM reports WHERE id=:reportId FOR UPDATE
                """, Map.of("reportId", reportId), (rs, rowNum) -> new LockedReport(
                rs.getLong("id"), rs.getLong("reported_member_id"), rs.getString("status")))
                .stream().findFirst();
    }

    public int markReportActionTaken(long reportId, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE reports SET status='ACTION_TAKEN', updated_at=:now
                WHERE id=:reportId AND status='RESOLVED'
                """, Map.of("reportId", reportId, "now", now));
    }

    public Optional<ExistingAction> findByIdempotencyKey(UUID key) {
        return jdbc.query("""
                SELECT id, target_member_id, action_type, metadata->>'requestFingerprint' AS fingerprint
                FROM admin_actions WHERE idempotency_key=:key
                """, Map.of("key", key), (rs, rowNum) -> new ExistingAction(
                rs.getLong("id"), rs.getLong("target_member_id"),
                rs.getString("action_type"), rs.getString("fingerprint"))).stream().findFirst();
    }

    public void lockIdempotencyKey(UUID key) {
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))",
                Map.of("key", key), resultSet -> null);
    }

    public long insertAction(
            long adminMemberId,
            long targetMemberId,
            Long reportId,
            AdminMemberActionRequest request,
            UUID idempotencyKey,
            String fingerprint,
            OffsetDateTime now,
            String beforeStatus,
            String afterStatus,
            OffsetDateTime suspendedUntil
    ) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("adminMemberId", adminMemberId);
        parameters.put("targetMemberId", targetMemberId);
        parameters.put("reportId", reportId == null ? new org.springframework.jdbc.core.SqlParameterValue(
                java.sql.Types.BIGINT, null) : reportId);
        parameters.put("actionType", request.action().name());
        parameters.put("reasonCode", request.reasonCode().name());
        parameters.put("reason", normalize(request.reasonNote()));
        parameters.put("idempotencyKey", idempotencyKey);
        parameters.put("fingerprint", fingerprint);
        parameters.put("beforeStatus", beforeStatus);
        parameters.put("afterStatus", afterStatus);
        parameters.put("suspendedUntil", suspendedUntil == null ? null : suspendedUntil.toString());
        parameters.put("createdAt", now);
        Long id = jdbc.queryForObject("""
                INSERT INTO admin_actions(
                    admin_member_id, target_member_id, report_id, action_type, reason,
                    reason_code, idempotency_key, metadata, created_at
                ) VALUES (
                    :adminMemberId, :targetMemberId, :reportId, :actionType, :reason,
                    :reasonCode, :idempotencyKey,
                    jsonb_build_object(
                        'requestFingerprint', :fingerprint,
                        'beforeStatus', :beforeStatus,
                        'afterStatus', :afterStatus,
                        'suspendedUntil', CAST(:suspendedUntil AS text)
                    ), :createdAt
                ) RETURNING id
                """, parameters, Long.class);
        return Objects.requireNonNull(id);
    }

    public int restoreExpiredSuspensions(OffsetDateTime now, int batchSize) {
        return jdbc.update("""
                WITH targets AS (
                    SELECT id FROM members
                    WHERE status='SUSPENDED' AND suspended_until <= :now
                    ORDER BY suspended_until, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                )
                UPDATE members m
                SET status=m.status_before_sanction, status_before_sanction=NULL,
                    suspended_at=NULL, suspended_until=NULL, updated_at=:now
                FROM targets WHERE m.id=targets.id
                """, Map.of("now", now, "batchSize", batchSize));
    }

    private AdminMemberListItemResponse mapListItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminMemberListItemResponse(
                rs.getLong("id"), rs.getString("nickname"), rs.getString("profile_image_url"),
                rs.getString("role"), AdminMemberStatus.valueOf(rs.getString("status")),
                rs.getInt("penalty_score"), rs.getBigDecimal("manner_temperature"),
                dateTime(rs, "suspended_until"), dateTime(rs, "created_at"));
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static OffsetDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    public record LockedReport(long reportId, long reportedMemberId, String status) {
    }

    public record ExistingAction(long actionId, long targetMemberId, String actionType, String fingerprint) {
    }
}
