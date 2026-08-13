package com.survey.meetorsolo.domain.safety.report.repository;

import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MatchReportRepository {

    private final JdbcTemplate jdbc;

    public MatchReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<GroupSnapshot> findGroupForShare(long groupId) {
        return jdbc.query("""
                SELECT id, status, completed_at, cancelled_at
                FROM match_groups
                WHERE id = ?
                FOR SHARE
                """, GROUP_ROW_MAPPER, groupId).stream().findFirst();
    }

    public boolean existsParticipant(long groupId, long memberId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM match_group_members
                    WHERE group_id = ? AND member_id = ?
                )
                """, Boolean.class, groupId, memberId);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<ReportSnapshot> insertIfAbsent(
            long reporterMemberId,
            long reportedMemberId,
            long groupId,
            MatchReportReasonCode reasonCode,
            OffsetDateTime now
    ) {
        List<ReportSnapshot> inserted = jdbc.query("""
                INSERT INTO reports(
                    reporter_member_id, reported_member_id, group_id, reason_code,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'SUBMITTED', ?, ?)
                ON CONFLICT (reporter_member_id, reported_member_id, group_id, reason_code)
                DO NOTHING
                RETURNING id, group_id, reported_member_id, reason_code, status, created_at
                """, REPORT_ROW_MAPPER, reporterMemberId, reportedMemberId, groupId,
                reasonCode.name(), now, now);
        return inserted.stream().findFirst();
    }

    public Optional<ReportSnapshot> findExisting(
            long reporterMemberId,
            long reportedMemberId,
            long groupId,
            MatchReportReasonCode reasonCode
    ) {
        return jdbc.query("""
                SELECT id, group_id, reported_member_id, reason_code, status, created_at
                FROM reports
                WHERE reporter_member_id = ?
                  AND reported_member_id = ?
                  AND group_id = ?
                  AND reason_code = ?
                """, REPORT_ROW_MAPPER, reporterMemberId, reportedMemberId, groupId,
                reasonCode.name()).stream().findFirst();
    }

    private static final RowMapper<GroupSnapshot> GROUP_ROW_MAPPER = (rs, rowNum) ->
            new GroupSnapshot(
                    rs.getLong("id"),
                    rs.getString("status"),
                    offsetDateTime(rs, "completed_at"),
                    offsetDateTime(rs, "cancelled_at")
            );

    private static final RowMapper<ReportSnapshot> REPORT_ROW_MAPPER = (rs, rowNum) ->
            new ReportSnapshot(
                    rs.getLong("id"),
                    rs.getLong("group_id"),
                    rs.getLong("reported_member_id"),
                    MatchReportReasonCode.valueOf(rs.getString("reason_code")),
                    rs.getString("status"),
                    rs.getObject("created_at", OffsetDateTime.class)
            );

    private static OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    public record GroupSnapshot(
            long groupId,
            String status,
            OffsetDateTime completedAt,
            OffsetDateTime cancelledAt
    ) {
    }

    public record ReportSnapshot(
            long reportId,
            long groupId,
            long reportedMemberId,
            MatchReportReasonCode reasonCode,
            String status,
            OffsetDateTime createdAt
    ) {
    }
}
