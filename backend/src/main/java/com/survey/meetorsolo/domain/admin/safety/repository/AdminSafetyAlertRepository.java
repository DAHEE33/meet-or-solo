package com.survey.meetorsolo.domain.admin.safety.repository;

import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertResponse;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertStatus;
import com.survey.meetorsolo.domain.admin.safety.dto.AdminSafetyAlertType;
import com.survey.meetorsolo.domain.admin.safety.service.AdminSafetyAlertCursorCodec.Cursor;
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
public class AdminSafetyAlertRepository {

    private static final String SELECT_COLUMNS = """
            SELECT a.id AS alert_id, a.alert_type, a.status, a.trigger_report_id,
                   a.valid_report_count, a.handled_at, a.created_at,
                   m.id AS reported_member_id, m.nickname AS reported_nickname,
                   m.profile_image_url AS reported_profile_image_url,
                   m.status AS reported_member_status
            FROM admin_safety_alerts a
            JOIN members m ON m.id = a.reported_member_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminSafetyAlertRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 누적 임계 도달 알림을 생성한다.
     *
     * <p>같은 회원에게 이미 미확인({@code OPEN}) 알림이 있으면 생성하지 않는다. 임계를 넘긴
     * 뒤의 모든 유효 판정마다 알림이 쌓이는 것을 막기 위한 조건이다. 관리자가 조치해
     * {@code CLOSED}가 된 뒤 새 신고가 누적되면 다시 생성된다.
     *
     * <p>{@code trigger_report_id} unique 제약이 같은 신고의 재판정에 대한 멱등성을 보장한다.
     *
     * @return 실제로 생성했으면 {@code true}
     */
    public boolean insertIfAbsent(
            long reportedMemberId, long triggerReportId, long validReportCount, OffsetDateTime now) {
        int inserted = jdbc.update("""
                INSERT INTO admin_safety_alerts(
                    reported_member_id, alert_type, trigger_report_id, valid_report_count,
                    status, created_at, updated_at
                )
                SELECT :reportedMemberId, 'REPORT_THRESHOLD', :triggerReportId,
                       :validReportCount, 'OPEN', :now, :now
                WHERE NOT EXISTS (
                    SELECT 1 FROM admin_safety_alerts
                    WHERE reported_member_id = :reportedMemberId AND status = 'OPEN'
                )
                ON CONFLICT (trigger_report_id) DO NOTHING
                """, Map.of(
                "reportedMemberId", reportedMemberId,
                "triggerReportId", triggerReportId,
                "validReportCount", validReportCount,
                "now", now));
        return inserted == 1;
    }

    public List<AdminSafetyAlertResponse> findPage(
            AdminSafetyAlertStatus status, Cursor cursor, int fetchSize) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE 1=1");
        Map<String, Object> parameters = new HashMap<>();
        if (status != null) {
            sql.append(" AND a.status = :status");
            parameters.put("status", status.name());
        }
        if (cursor != null) {
            sql.append(" AND (a.created_at < :cursorCreatedAt OR "
                    + "(a.created_at = :cursorCreatedAt AND a.id < :cursorAlertId))");
            parameters.put("cursorCreatedAt", cursor.createdAt());
            parameters.put("cursorAlertId", cursor.alertId());
        }
        sql.append(" ORDER BY a.created_at DESC, a.id DESC LIMIT :fetchSize");
        parameters.put("fetchSize", fetchSize);
        return jdbc.query(sql.toString(), parameters, this::map);
    }

    public int countOpen() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_safety_alerts WHERE status = 'OPEN'",
                Map.of(), Integer.class);
        return count == null ? 0 : count;
    }

    public Optional<AdminSafetyAlertResponse> findById(long alertId) {
        return jdbc.query(SELECT_COLUMNS + " WHERE a.id = :alertId",
                Map.of("alertId", alertId), this::map).stream().findFirst();
    }

    public Optional<LockedAlert> findByIdForUpdate(long alertId) {
        return jdbc.query("""
                SELECT id, reported_member_id, status
                FROM admin_safety_alerts WHERE id = :alertId FOR UPDATE
                """, Map.of("alertId", alertId), (rs, rowNum) -> new LockedAlert(
                rs.getLong("id"), rs.getLong("reported_member_id"), rs.getString("status")))
                .stream().findFirst();
    }

    public int acknowledge(long alertId, long adminMemberId, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE admin_safety_alerts
                SET status = 'ACKNOWLEDGED', handled_admin_id = :adminMemberId,
                    handled_at = :now, updated_at = :now
                WHERE id = :alertId AND status = 'OPEN'
                """, Map.of("alertId", alertId, "adminMemberId", adminMemberId, "now", now));
    }

    /**
     * 관리자가 회원을 제재하면 그 회원의 미종료 알림을 모두 종료한다.
     * 같은 제재 transaction에서 호출해 중복 대응을 막는다.
     */
    public int closeByMemberId(long reportedMemberId, long adminMemberId, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE admin_safety_alerts
                SET status = 'CLOSED', handled_admin_id = :adminMemberId,
                    handled_at = :now, updated_at = :now
                WHERE reported_member_id = :reportedMemberId AND status <> 'CLOSED'
                """, Map.of(
                "reportedMemberId", reportedMemberId,
                "adminMemberId", adminMemberId,
                "now", now));
    }

    private AdminSafetyAlertResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new AdminSafetyAlertResponse(
                rs.getLong("alert_id"),
                AdminSafetyAlertType.valueOf(rs.getString("alert_type")),
                AdminSafetyAlertStatus.valueOf(rs.getString("status")),
                rs.getLong("reported_member_id"),
                rs.getString("reported_nickname"),
                rs.getString("reported_profile_image_url"),
                rs.getString("reported_member_status"),
                rs.getLong("trigger_report_id"),
                rs.getInt("valid_report_count"),
                dateTime(rs, "handled_at"),
                dateTime(rs, "created_at"));
    }

    private static OffsetDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    public record LockedAlert(long alertId, long reportedMemberId, String status) {
    }
}
