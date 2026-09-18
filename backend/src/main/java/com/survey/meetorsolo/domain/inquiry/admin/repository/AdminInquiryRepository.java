package com.survey.meetorsolo.domain.inquiry.admin.repository;

import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryListItemResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryMemberSummaryResponse;
import com.survey.meetorsolo.domain.inquiry.admin.service.AdminInquiryCursorCodec.Cursor;
import com.survey.meetorsolo.domain.inquiry.admin.service.AdminInquiryFilter;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryPriority;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 문의 목록 조회. {@code AdminReportRepository}와 같이 목록만 JDBC로 다룬다 —
 * filter 조합이 동적이고 회원 join이 필요해 JPQL보다 SQL이 읽기 쉽다.
 *
 * <p>상세와 상태 변경은 {@code InquiryRepository}(JPA)를 그대로 쓴다. 엔티티 상태 머신
 * 메서드를 재사용하는 편이 안전하고, 목록과 달리 동적 filter가 없다.
 */
@Repository
public class AdminInquiryRepository {

    private static final String SELECT_LIST = """
            SELECT i.id AS inquiry_id, i.category, i.title, i.status, i.priority,
                   i.last_message_at, i.created_at,
                   m.id AS member_id, m.nickname AS member_nickname, m.status AS member_status,
                   (SELECT COUNT(*) FROM inquiry_messages msg WHERE msg.inquiry_id = i.id)
                       AS message_count
            FROM inquiries i
            JOIN members m ON m.id = i.member_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminInquiryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 정렬 키는 {@code (created_at DESC, id DESC)}다. cursor payload와 반드시 같아야 한다.
     *
     * <p><b>긴급 우선 정렬을 넣지 않았다.</b> {@code ORDER BY}에 {@code priority}를 넣으면
     * cursor에도 그 값이 들어가야 하고, 정렬 키와 cursor 키가 어긋나면 페이지 경계에서 항목이
     * 중복·누락된다. 대신 {@code priority} filter를 제공한다(docs/29 5.7).
     */
    public List<AdminInquiryListItemResponse> findPage(
            AdminInquiryFilter filter, Cursor cursor, int fetchSize) {
        StringBuilder sql = new StringBuilder(SELECT_LIST).append(" WHERE 1=1");
        Map<String, Object> parameters = new HashMap<>();
        appendFilter(sql, parameters, filter);
        if (cursor != null) {
            sql.append(" AND (i.created_at < :cursorCreatedAt OR "
                    + "(i.created_at = :cursorCreatedAt AND i.id < :cursorInquiryId))");
            parameters.put("cursorCreatedAt", cursor.createdAt());
            parameters.put("cursorInquiryId", cursor.inquiryId());
        }
        sql.append(" ORDER BY i.created_at DESC, i.id DESC LIMIT :fetchSize");
        parameters.put("fetchSize", fetchSize);
        return jdbc.query(sql.toString(), parameters, this::mapListItem);
    }

    /**
     * 미처리 문의 수. 관리자 메뉴 badge가 쓴다. filter와 무관한 전체 미처리 건수다 —
     * badge는 "지금 봐야 할 일이 몇 건인지"를 알리는 값이므로 화면 filter에 따라 흔들려선 안 된다.
     */
    public long countOpen() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM inquiries WHERE status IN ('RECEIVED', 'IN_PROGRESS')
                """, Map.of(), Long.class);
        return count == null ? 0L : count;
    }

    public Optional<AdminInquiryMemberSummaryResponse> findMemberSummary(long inquiryId) {
        return jdbc.query("""
                SELECT m.id AS member_id, m.nickname AS member_nickname, m.status AS member_status
                FROM inquiries i
                JOIN members m ON m.id = i.member_id
                WHERE i.id = :inquiryId
                """, Map.of("inquiryId", inquiryId), (rs, rowNum) -> member(rs))
                .stream()
                .findFirst();
    }

    private void appendFilter(
            StringBuilder sql, Map<String, Object> parameters, AdminInquiryFilter filter) {
        if (filter.status() != null) {
            sql.append(" AND i.status = :status");
            parameters.put("status", filter.status().name());
        }
        if (filter.category() != null) {
            sql.append(" AND i.category = :category");
            parameters.put("category", filter.category().name());
        }
        if (filter.priority() != null) {
            sql.append(" AND i.priority = :priority");
            parameters.put("priority", filter.priority().name());
        }
        if (filter.createdFrom() != null) {
            sql.append(" AND i.created_at >= :createdFrom");
            parameters.put("createdFrom", filter.createdFrom());
        }
        if (filter.createdTo() != null) {
            sql.append(" AND i.created_at < :createdTo");
            parameters.put("createdTo", filter.createdTo());
        }
    }

    private AdminInquiryListItemResponse mapListItem(ResultSet rs, int rowNum) throws SQLException {
        return new AdminInquiryListItemResponse(
                rs.getLong("inquiry_id"),
                InquiryCategory.valueOf(rs.getString("category")),
                rs.getString("title"),
                InquiryStatus.valueOf(rs.getString("status")),
                InquiryPriority.valueOf(rs.getString("priority")),
                member(rs),
                rs.getInt("message_count"),
                dateTime(rs, "last_message_at"),
                dateTime(rs, "created_at"));
    }

    private static AdminInquiryMemberSummaryResponse member(ResultSet rs) throws SQLException {
        return new AdminInquiryMemberSummaryResponse(
                rs.getLong("member_id"),
                rs.getString("member_nickname"),
                rs.getString("member_status"));
    }

    private static OffsetDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }
}
