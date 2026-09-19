package com.survey.meetorsolo.domain.admin.dashboard.repository;

import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardPopularFestivalResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardRecentInquiryResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardRecentReportResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 대시보드 집계 전용 조회. 다른 admin repository와 같이 {@code NamedParameterJdbcTemplate}를 쓴다.
 *
 * <p>집계마다 query를 나눈다. 하나의 query로 묶으면 cross join이나 상관 subquery가 생기는데,
 * 대상 테이블(members / match_groups / festival_checkins / reports / inquiries)이 서로
 * 관계가 없어 묶을 이유가 없다. 각 query는 index를 그대로 탄다.
 */
@Repository
public class AdminDashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AdminDashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 가입 회원 수.
     *
     * <p>탈퇴({@code WITHDRAWN})와 삭제({@code DELETED})만 제외한다. 프로필 입력 전
     * ({@code PROFILE_REQUIRED})과 정지({@code SUSPENDED})는 계정이 남아 있으므로 센다.
     * 관리자 계정도 {@code members} 행이라 함께 세는데, {@code /admin/members} 목록이
     * 역할 filter 없이 같은 범위를 보여주므로 두 화면의 숫자가 어긋나지 않는다.
     */
    public long countMembers() {
        return count("SELECT count(*) FROM members WHERE status NOT IN ('WITHDRAWN', 'DELETED')",
                Map.of());
    }

    /**
     * {@code since} 이후 성사된 매칭 수. 오늘 0시(Asia/Seoul)를 넘겨 "오늘 매칭"을 센다.
     *
     * <p>{@code status}를 보지 않는다. {@code match_groups} 행은 매칭이 성사된 순간 생기고
     * {@code confirmed_at}은 그 뒤 바뀌지 않는다. 취소({@code CANCELLED})를 빼면 오늘 성사된
     * 매칭 수가 오늘 저녁에 줄어드는, 누적 지표로 읽히지 않는 값이 된다.
     */
    public long countMatchGroupsConfirmedSince(OffsetDateTime since) {
        return count("SELECT count(*) FROM match_groups WHERE confirmed_at >= :since",
                Map.of("since", since));
    }

    /**
     * 누적 체크인 수.
     *
     * <p>만료({@code EXPIRED})는 세고 취소({@code CANCELLED})는 뺀다. 만료는 현장에 갔다는
     * 사실이 남은 것이고, 취소는 사용자가 체크인 자체를 되돌린 것이다.
     */
    public long countCheckins() {
        return count("SELECT count(*) FROM festival_checkins WHERE status <> 'CANCELLED'", Map.of());
    }

    /** 체크인 수 상위 축제. 동수일 때는 id 오름차순으로 고정해 호출마다 순서가 흔들리지 않게 한다. */
    public List<AdminDashboardPopularFestivalResponse> findPopularFestivals(int limit) {
        return jdbc.query("""
                SELECT f.id AS festival_id, f.title, count(*) AS checkin_count
                FROM festival_checkins c
                JOIN festivals f ON f.id = c.festival_id
                WHERE c.status <> 'CANCELLED'
                GROUP BY f.id, f.title
                ORDER BY checkin_count DESC, f.id
                LIMIT :limit
                """, Map.of("limit", limit), (rs, rowNum) -> new AdminDashboardPopularFestivalResponse(
                rs.getLong("festival_id"), rs.getString("title"), rs.getLong("checkin_count")));
    }

    /** 최근 신고. 신고 대상 닉네임은 다른 admin 조회와 같은 규칙으로 탈퇴 회원을 가린다. */
    public List<AdminDashboardRecentReportResponse> findRecentReports(int limit) {
        return jdbc.query("""
                SELECT r.id AS report_id, r.reason_code, r.status, r.created_at,
                       CASE WHEN m.status = 'WITHDRAWN' THEN '탈퇴한 회원'
                            ELSE m.nickname END AS reported_nickname
                FROM reports r
                JOIN members m ON m.id = r.reported_member_id
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT :limit
                """, Map.of("limit", limit), (rs, rowNum) -> new AdminDashboardRecentReportResponse(
                rs.getLong("report_id"), rs.getString("reason_code"), rs.getString("status"),
                rs.getString("reported_nickname"), dateTime(rs, "created_at")));
    }

    /**
     * 최근 문의.
     *
     * <p>보관 기간이 지나 익명화된 문의를 따로 거르지 않는다. 익명화는 제목을 지우는 것이
     * 아니라 안내 문구로 덮는 처리라 그대로 보여줘도 읽히고, 익명화 대상은 종결 후 1년이
     * 지난 문의뿐이라 최신 {@code limit}건에 들어올 일도 사실상 없다.
     */
    public List<AdminDashboardRecentInquiryResponse> findRecentInquiries(int limit) {
        return jdbc.query("""
                SELECT id AS inquiry_id, category, title, status, created_at
                FROM inquiries
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """, Map.of("limit", limit), (rs, rowNum) -> new AdminDashboardRecentInquiryResponse(
                rs.getLong("inquiry_id"), rs.getString("category"), rs.getString("title"),
                rs.getString("status"), dateTime(rs, "created_at")));
    }

    private long count(String sql, Map<String, ?> parameters) {
        Long value = jdbc.queryForObject(sql, parameters, Long.class);
        return value == null ? 0L : value;
    }

    private static OffsetDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }
}
