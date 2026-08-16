package com.survey.meetorsolo.domain.safety.report.admin.repository;

import com.survey.meetorsolo.domain.safety.report.admin.dto.*;
import com.survey.meetorsolo.domain.safety.report.admin.service.AdminReportCursorCodec.Cursor;
import com.survey.meetorsolo.domain.safety.report.admin.service.AdminReportFilter;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminReportRepository {

    private static final String SELECT_COLUMNS = """
            SELECT r.id AS report_id, r.group_id, r.reason_code, r.status AS report_status,
                   r.created_at, r.updated_at, r.resolved_at,
                   reporter.id AS reporter_id, reporter.nickname AS reporter_nickname,
                   reporter.profile_image_url AS reporter_profile_image_url,
                   reporter.status AS reporter_status,
                   reported.id AS reported_id, reported.nickname AS reported_nickname,
                   reported.profile_image_url AS reported_profile_image_url,
                   reported.status AS reported_status,
                   g.status AS group_status, g.confirmed_at AS group_confirmed_at
            FROM reports r
            JOIN members reporter ON reporter.id = r.reporter_member_id
            JOIN members reported ON reported.id = r.reported_member_id
            LEFT JOIN match_groups g ON g.id = r.group_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminReportDetailResponse> findPage(
            AdminReportFilter filter, Cursor cursor, int fetchSize) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE 1=1");
        Map<String, Object> parameters = new HashMap<>();
        appendFilter(sql, parameters, filter);
        if (cursor != null) {
            sql.append(" AND (r.created_at < :cursorCreatedAt OR "
                    + "(r.created_at = :cursorCreatedAt AND r.id < :cursorReportId))");
            parameters.put("cursorCreatedAt", cursor.createdAt());
            parameters.put("cursorReportId", cursor.reportId());
        }
        sql.append(" ORDER BY r.created_at DESC, r.id DESC LIMIT :fetchSize");
        parameters.put("fetchSize", fetchSize);
        return jdbc.query(sql.toString(), parameters, this::mapDetail);
    }

    public Optional<AdminReportDetailResponse> findDetail(long reportId) {
        return queryOne(SELECT_COLUMNS + " WHERE r.id = :reportId", reportId);
    }

    public Optional<AdminReportDetailResponse> findDetailForUpdate(long reportId) {
        return queryOne(SELECT_COLUMNS + " WHERE r.id = :reportId FOR UPDATE OF r", reportId);
    }

    public int updateStatus(
            long reportId, AdminReportStatus targetStatus, OffsetDateTime now, boolean terminal) {
        return jdbc.update("""
                UPDATE reports
                SET status = :status, updated_at = :now, resolved_at = :resolvedAt
                WHERE id = :reportId
                """, Map.of(
                "status", targetStatus.name(),
                "now", now,
                "resolvedAt", terminal ? now : SqlNull.OFFSET_DATE_TIME,
                "reportId", reportId));
    }

    public void insertAdminAction(
            long adminMemberId, long targetMemberId, long reportId, String actionType,
            OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO admin_actions(
                    admin_member_id, target_member_id, report_id, action_type, created_at
                ) VALUES (
                    :adminMemberId, :targetMemberId, :reportId, :actionType, :createdAt
                )
                """, Map.of(
                "adminMemberId", adminMemberId,
                "targetMemberId", targetMemberId,
                "reportId", reportId,
                "actionType", actionType,
                "createdAt", now));
    }

    private Optional<AdminReportDetailResponse> queryOne(String sql, long reportId) {
        return jdbc.query(sql, Map.of("reportId", reportId), this::mapDetail).stream().findFirst();
    }

    private void appendFilter(
            StringBuilder sql, Map<String, Object> parameters, AdminReportFilter filter) {
        if (filter.status() != null) {
            sql.append(" AND r.status = :status");
            parameters.put("status", filter.status().name());
        }
        if (filter.reason() != null) {
            sql.append(" AND r.reason_code = :reason");
            parameters.put("reason", filter.reason().name());
        }
        if (filter.createdFrom() != null) {
            sql.append(" AND r.created_at >= :createdFrom");
            parameters.put("createdFrom", filter.createdFrom());
        }
        if (filter.createdTo() != null) {
            sql.append(" AND r.created_at < :createdTo");
            parameters.put("createdTo", filter.createdTo());
        }
    }

    private AdminReportDetailResponse mapDetail(ResultSet rs, int rowNum) throws SQLException {
        Long groupId = nullableLong(rs, "group_id");
        AdminReportGroupSummaryResponse group = groupId == null ? null
                : new AdminReportGroupSummaryResponse(
                        groupId, rs.getString("group_status"), dateTime(rs, "group_confirmed_at"));
        return new AdminReportDetailResponse(
                rs.getLong("report_id"),
                group,
                MatchReportReasonCode.valueOf(rs.getString("reason_code")),
                AdminReportStatus.valueOf(rs.getString("report_status")),
                member(rs, "reporter"),
                member(rs, "reported"),
                dateTime(rs, "created_at"),
                dateTime(rs, "updated_at"),
                dateTime(rs, "resolved_at"));
    }

    private AdminReportMemberSummaryResponse member(ResultSet rs, String prefix) throws SQLException {
        return new AdminReportMemberSummaryResponse(
                rs.getLong(prefix + "_id"),
                rs.getString(prefix + "_nickname"),
                rs.getString(prefix + "_profile_image_url"),
                rs.getString(prefix + "_status"));
    }

    private static OffsetDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private static final class SqlNull {
        private static final org.springframework.jdbc.core.SqlParameterValue OFFSET_DATE_TIME =
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.TIMESTAMP_WITH_TIMEZONE, null);
    }
}
