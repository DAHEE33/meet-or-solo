package com.survey.meetorsolo.domain.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardPopularFestivalResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardRecentInquiryResponse;
import com.survey.meetorsolo.domain.admin.dashboard.dto.AdminDashboardRecentReportResponse;
import com.survey.meetorsolo.domain.admin.dashboard.repository.AdminDashboardRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 집계 SQL을 실제 PostgreSQL로 검증한다. 집계·JOIN·정렬은 DB 없이 확인할 수 없다.
 *
 * <p><b>절대값을 단정하지 않는다.</b> 집계 query는 테이블 전체를 세므로 실행 환경에 이미
 * 들어 있는 행에 결과가 좌우된다. 그래서 개수는 삽입 전후의 증분으로, 목록은 이 테스트가
 * 넣은 id만 걸러 상대 순서로 확인한다. 목록 조회에 넉넉한 limit을 넘기는 것도 같은 이유다 —
 * 운영 데이터가 상위 5건을 채우고 있어도 테스트 데이터가 결과에 남는다.
 *
 * <p>모든 데이터는 {@code @Transactional} 롤백으로 정리된다.
 */
@SpringBootTest(properties = {
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.festival.sync.enabled=false",
        "app.tour-place.sync.enabled=false",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Transactional
class AdminDashboardIntegrationTest {

    private static final long MEMBER = 9_410_001L;
    private static final long WITHDRAWN_MEMBER = 9_410_002L;
    private static final long DELETED_MEMBER = 9_410_003L;
    private static final long REPORTER = 9_410_004L;
    private static final long POPULAR_FESTIVAL = 9_420_001L;
    private static final long QUIET_FESTIVAL = 9_420_002L;
    private static final int WIDE_LIMIT = 1_000;

    @Autowired AdminDashboardRepository dashboard;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 탈퇴와_삭제_회원은_총_사용자에서_뺀다() {
        long before = dashboard.countMembers();
        insertMember(MEMBER, "dashboard-active", "ACTIVE");
        insertWithdrawnMember(WITHDRAWN_MEMBER, "dashboard-withdrawn");
        insertMember(DELETED_MEMBER, "dashboard-deleted", "DELETED");

        assertThat(dashboard.countMembers() - before).isEqualTo(1);
    }

    @Test
    void 취소된_체크인은_누적에서_빼고_만료된_체크인은_센다() {
        insertMember(MEMBER, "dashboard-checkin", "ACTIVE");
        insertFestival(POPULAR_FESTIVAL, "dashboard-festival-1", "체크인 많은 축제");
        insertFestival(QUIET_FESTIVAL, "dashboard-festival-2", "체크인 적은 축제");
        long before = dashboard.countCheckins();

        OffsetDateTime now = OffsetDateTime.now();
        insertCheckin(9_430_001L, MEMBER, POPULAR_FESTIVAL, "ACTIVE", now.minusMinutes(5));
        insertCheckin(9_430_002L, MEMBER, POPULAR_FESTIVAL, "EXPIRED", now.minusDays(1));
        insertCheckin(9_430_003L, MEMBER, QUIET_FESTIVAL, "CANCELLED", now.minusDays(2));

        assertThat(dashboard.countCheckins() - before).isEqualTo(2);
    }

    @Test
    void 인기_축제는_취소를_뺀_체크인_수_내림차순이다() {
        insertMember(MEMBER, "dashboard-popular", "ACTIVE");
        insertFestival(POPULAR_FESTIVAL, "dashboard-festival-1", "체크인 많은 축제");
        insertFestival(QUIET_FESTIVAL, "dashboard-festival-2", "체크인 적은 축제");

        OffsetDateTime now = OffsetDateTime.now();
        insertCheckin(9_430_001L, MEMBER, POPULAR_FESTIVAL, "EXPIRED", now.minusDays(3));
        insertCheckin(9_430_002L, MEMBER, POPULAR_FESTIVAL, "EXPIRED", now.minusDays(2));
        insertCheckin(9_430_003L, MEMBER, POPULAR_FESTIVAL, "CANCELLED", now.minusDays(1));
        insertCheckin(9_430_004L, MEMBER, QUIET_FESTIVAL, "EXPIRED", now.minusDays(3));

        List<AdminDashboardPopularFestivalResponse> mine =
                dashboard.findPopularFestivals(WIDE_LIMIT).stream()
                        .filter(festival -> festival.festivalId() == POPULAR_FESTIVAL
                                || festival.festivalId() == QUIET_FESTIVAL)
                        .toList();

        assertThat(mine).extracting(AdminDashboardPopularFestivalResponse::title)
                .containsExactly("체크인 많은 축제", "체크인 적은 축제");
        // 취소 1건은 빠져 2건이다.
        assertThat(mine.get(0).checkinCount()).isEqualTo(2);
        assertThat(mine.get(1).checkinCount()).isEqualTo(1);
    }

    @Test
    void 체크인이_없는_축제는_인기_목록에_나오지_않는다() {
        insertFestival(QUIET_FESTIVAL, "dashboard-festival-2", "체크인 없는 축제");

        assertThat(dashboard.findPopularFestivals(WIDE_LIMIT))
                .extracting(AdminDashboardPopularFestivalResponse::festivalId)
                .doesNotContain(QUIET_FESTIVAL);
    }

    @Test
    void 오늘_매칭은_경계_시각_이후_성사분만_센다() {
        insertMember(MEMBER, "dashboard-match", "ACTIVE");
        insertFestival(POPULAR_FESTIVAL, "dashboard-festival-1", "매칭 축제");
        OffsetDateTime boundary = OffsetDateTime.now().minusHours(1);
        insertMatchGroup(9_440_001L, 9_450_001L, POPULAR_FESTIVAL, "CONFIRMED", boundary.minusMinutes(1));
        insertMatchGroup(9_440_002L, 9_450_002L, POPULAR_FESTIVAL, "CONFIRMED", boundary.plusMinutes(1));
        // 취소된 매칭도 "오늘 성사"에는 남는다.
        insertMatchGroup(9_440_003L, 9_450_003L, POPULAR_FESTIVAL, "CANCELLED", boundary.plusMinutes(2));

        long before = dashboard.countMatchGroupsConfirmedSince(boundary.minusMinutes(2));
        long after = dashboard.countMatchGroupsConfirmedSince(boundary);

        assertThat(before - after).isEqualTo(1);
        assertThat(after).isGreaterThanOrEqualTo(2);
    }

    @Test
    void 최근_신고는_최신순이고_탈퇴한_대상은_닉네임을_가린다() {
        insertMember(REPORTER, "dashboard-reporter", "ACTIVE");
        insertMember(MEMBER, "dashboard-reported", "ACTIVE");
        insertWithdrawnMember(WITHDRAWN_MEMBER, "dashboard-reported-withdrawn");
        OffsetDateTime now = OffsetDateTime.now();
        insertReport(9_460_001L, REPORTER, MEMBER, "NO_SHOW", "SUBMITTED", now.minusHours(2));
        insertReport(9_460_002L, REPORTER, WITHDRAWN_MEMBER, "RUDE", "REVIEWING", now.minusHours(1));

        List<AdminDashboardRecentReportResponse> mine =
                dashboard.findRecentReports(WIDE_LIMIT).stream()
                        .filter(report -> report.reportId() == 9_460_001L || report.reportId() == 9_460_002L)
                        .toList();

        assertThat(mine).extracting(AdminDashboardRecentReportResponse::reportId)
                .containsExactly(9_460_002L, 9_460_001L);
        assertThat(mine.get(0).reportedNickname()).isEqualTo("탈퇴한 회원");
        assertThat(mine.get(0).reasonCode()).isEqualTo("RUDE");
        assertThat(mine.get(0).status()).isEqualTo("REVIEWING");
        assertThat(mine.get(1).reportedNickname()).isEqualTo("dashboard-reported");
    }

    @Test
    void 최근_문의는_최신순으로_제목과_분류를_함께_내린다() {
        insertMember(MEMBER, "dashboard-inquirer", "ACTIVE");
        OffsetDateTime now = OffsetDateTime.now();
        insertInquiry(9_470_001L, MEMBER, "BUG", "체크인이 안 돼요", "RECEIVED", now.minusHours(2));
        insertInquiry(9_470_002L, MEMBER, "MATCHING", "매칭이 계속 실패해요", "IN_PROGRESS", now.minusHours(1));

        List<AdminDashboardRecentInquiryResponse> mine =
                dashboard.findRecentInquiries(WIDE_LIMIT).stream()
                        .filter(inquiry -> inquiry.inquiryId() == 9_470_001L
                                || inquiry.inquiryId() == 9_470_002L)
                        .toList();

        assertThat(mine).extracting(AdminDashboardRecentInquiryResponse::title)
                .containsExactly("매칭이 계속 실패해요", "체크인이 안 돼요");
        assertThat(mine.get(0).category()).isEqualTo("MATCHING");
        assertThat(mine.get(0).status()).isEqualTo("IN_PROGRESS");
    }

    private void insertMember(long id, String providerUserId, String status) {
        jdbc.update("""
                INSERT INTO members (id, provider, provider_user_id, nickname, status, created_at, updated_at)
                VALUES (?, 'KAKAO', ?, ?, ?, now(), now())
                """, id, providerUserId, providerUserId, status);
    }

    /**
     * 탈퇴 회원. 닉네임은 NULL이어야 하고 탈퇴 스냅샷 4개가 모두 있어야 한다
     * ({@code chk_members_withdrawn_anonymized}, {@code chk_members_withdrawal_snapshot}).
     */
    private void insertWithdrawnMember(long id, String providerUserId) {
        jdbc.update("""
                INSERT INTO members (id, provider, provider_user_id, nickname, status, withdrawn_at,
                                     withdrawn_from_status, withdrawn_by_admin, withdrawn_rejoin_blocked,
                                     created_at, updated_at)
                VALUES (?, 'KAKAO', ?, NULL, 'WITHDRAWN', now(), 'ACTIVE', FALSE, FALSE, now(), now())
                """, id, providerUserId);
    }

    private void insertFestival(long id, String contentId, String title) {
        jdbc.update("""
                INSERT INTO festivals (id, content_id, content_type_id, title, address, status, map_x, map_y,
                                       created_at, updated_at)
                VALUES (?, ?, '15', ?, '강원 어딘가', 'ACTIVE', 128.1, 37.1, now(), now())
                """, id, contentId, title);
    }

    private void insertCheckin(
            long id, long memberId, long festivalId, String status, OffsetDateTime checkedInAt) {
        jdbc.update("""
                INSERT INTO festival_checkins (id, member_id, festival_id, distance_meters, status,
                                               checked_in_at, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 100, ?, ?, ?, now(), now())
                """, id, memberId, festivalId, status, checkedInAt, checkedInAt.plusHours(1));
    }

    private void insertMatchGroup(
            long groupId, long attemptId, long festivalId, String status, OffsetDateTime confirmedAt) {
        jdbc.update("""
                INSERT INTO match_attempts (id, festival_id, target_group_size, status, started_at,
                                            expires_at, confirmed_at, created_at, updated_at)
                VALUES (?, ?, 2, 'CONFIRMED', ?, ?, ?, now(), now())
                """, attemptId, festivalId, confirmedAt, confirmedAt.plusHours(1), confirmedAt);
        jdbc.update("""
                INSERT INTO match_groups (id, attempt_id, festival_id, status, confirmed_member_count,
                                          confirmed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 2, ?, now(), now())
                """, groupId, attemptId, festivalId, status, confirmedAt);
    }

    private void insertReport(
            long id,
            long reporterId,
            long reportedId,
            String reasonCode,
            String status,
            OffsetDateTime createdAt
    ) {
        jdbc.update("""
                INSERT INTO reports (id, reporter_member_id, reported_member_id, reason_code, status,
                                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                """, id, reporterId, reportedId, reasonCode, status, createdAt);
    }

    private void insertInquiry(
            long id,
            long memberId,
            String category,
            String title,
            String status,
            OffsetDateTime createdAt
    ) {
        jdbc.update("""
                INSERT INTO inquiries (id, member_id, category, title, status, priority,
                                       last_message_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'NORMAL', ?, ?, now())
                """, id, memberId, category, title, status, createdAt, createdAt);
    }
}
