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

    /**
     * 그룹에 실제로 만남 장소에 도착한 사람이 있었는지({@code docs/19} 4.11.1).
     *
     * <p>판정 기준을 {@code status = 'ARRIVED'}가 아니라 {@code arrived_at IS NOT NULL}로 둔다.
     * 도착한 뒤에도 status는 완료 시 {@code COMPLETED}, 그룹 취소 시 {@code LEFT}로 바뀌지만
     * {@code arrived_at}은 지워지지 않는다. status로 판정하면 같은 만남이 그룹 상태에 따라
     * 신고 가능해졌다 불가능해졌다 한다.
     */
    public boolean existsArrival(long groupId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM match_group_members
                    WHERE group_id = ? AND arrived_at IS NOT NULL
                )
                """, Boolean.class, groupId);
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

    /**
     * 내가 이미 신고한 (group, 상대) 쌍이다.
     *
     * <p>{@code reports}의 유니크 키는 사유까지 포함한 (reporter, reported, group, reason_code)라
     * 같은 상대를 다른 사유로 여러 번 신고할 수 있다. 화면의 "신고됨" 표시는 사유와 무관하게
     * "이 만남에서 이 사람을 신고한 적이 있다"를 뜻하므로 DISTINCT로 사유를 접는다.
     */
    public List<ReportedPair> findReportedPairs(long reporterMemberId, List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(groupIds.size(), "?"));
        Object[] arguments = new Object[groupIds.size() + 1];
        arguments[0] = reporterMemberId;
        for (int index = 0; index < groupIds.size(); index++) {
            arguments[index + 1] = groupIds.get(index);
        }
        return jdbc.query("""
                SELECT DISTINCT group_id, reported_member_id
                FROM reports
                WHERE reporter_member_id = ?
                  AND group_id IN (%s)
                """.formatted(placeholders), REPORTED_PAIR_ROW_MAPPER, arguments);
    }

    public record ReportedPair(long groupId, long reportedMemberId) {
    }

    private static final RowMapper<ReportedPair> REPORTED_PAIR_ROW_MAPPER = (rs, rowNum) ->
            new ReportedPair(rs.getLong("group_id"), rs.getLong("reported_member_id"));

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
