package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.festival.dto.FestivalListItemResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListSort;
import com.survey.meetorsolo.domain.festival.dto.FestivalProgressFilter;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 축제 목록의 기간 선택·진행 상태 필터·집계 정렬을 실제 PostgreSQL로 검증한다.
 *
 * <p>이 셋은 전부 native query 안에서 일어난다 — 기간 겹침 판정, {@code ENDED}까지 여는 가시성
 * 전환, 파생 테이블로 집계한 뒤 {@code CASE}로 고르는 정렬. DB 없이는 확인할 수 없다.
 *
 * <p>같은 도메인의 {@code FestivalCheckinServiceIntegrationTest}와 같이 실행 환경의
 * PostgreSQL을 그대로 쓴다. 모든 데이터는 {@code @Transactional} 롤백으로 정리된다.
 */
@SpringBootTest(properties = {
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.festival.sync.enabled=false",
        "app.tour-place.sync.enabled=false",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Transactional
class FestivalListFilterIntegrationTest {

    private static final long MEMBER = 9_410_001L;
    private static final long OTHER_MEMBER = 9_410_002L;

    private static final long UPCOMING = 9_420_001L;
    private static final long ONGOING = 9_420_002L;
    private static final long ENDED_BY_DATE = 9_420_003L;
    private static final long ENDED_BY_STATUS = 9_420_004L;

    private static final String KEYWORD = "목록필터테스트";

    @Autowired FestivalQueryService festivals;
    @Autowired JdbcTemplate jdbc;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        insertMember(MEMBER, "festival-list-me");
        insertMember(OTHER_MEMBER, "festival-list-other");
        insertFestival(UPCOMING, "list-upcoming", KEYWORD + " 예정",
                today.plusDays(10), today.plusDays(12), "ACTIVE");
        insertFestival(ONGOING, "list-ongoing", KEYWORD + " 진행중",
                today.minusDays(1), today.plusDays(1), "ACTIVE");
        // 종료일은 지났는데 동기화가 아직 상태를 내리지 않은 축제.
        insertFestival(ENDED_BY_DATE, "list-ended-date", KEYWORD + " 날짜마감",
                today.minusDays(10), today.minusDays(5), "ACTIVE");
        // 동기화가 상태를 ENDED로 내린 축제.
        insertFestival(ENDED_BY_STATUS, "list-ended-status", KEYWORD + " 상태마감",
                today.minusDays(20), today.minusDays(15), "ENDED");
    }

    @Test
    void progress를_넘기지_않으면_종료된_축제는_기존처럼_보이지_않는다() {
        // 홈 화면과 관광지 상세가 같은 API를 쓴다. 기본 동작이 바뀌면 그쪽에 지난 축제가 섞인다.
        FestivalListResponse response = list(null, null, null, null);

        assertThat(ids(response)).containsExactlyInAnyOrder(UPCOMING, ONGOING);
    }

    @Test
    void progress가_ALL이면_진행_전_중_마감을_모두_돌려준다() {
        FestivalListResponse response = list(null, null, FestivalProgressFilter.ALL, null);

        assertThat(ids(response))
                .containsExactlyInAnyOrder(UPCOMING, ONGOING, ENDED_BY_DATE, ENDED_BY_STATUS);
    }

    @Test
    void progress가_ENDED면_날짜로_끝난_축제와_상태가_ENDED인_축제를_함께_돌려준다() {
        FestivalListResponse response = list(null, null, FestivalProgressFilter.ENDED, null);

        assertThat(ids(response)).containsExactlyInAnyOrder(ENDED_BY_DATE, ENDED_BY_STATUS);
    }

    @Test
    void progress가_UPCOMING이면_아직_시작하지_않은_축제만_돌려준다() {
        FestivalListResponse response = list(null, null, FestivalProgressFilter.UPCOMING, null);

        assertThat(ids(response)).containsExactly(UPCOMING);
    }

    @Test
    void progress가_ONGOING이면_오늘_열리고_있는_축제만_돌려준다() {
        FestivalListResponse response = list(null, null, FestivalProgressFilter.ONGOING, null);

        assertThat(ids(response)).containsExactly(ONGOING);
    }

    @Test
    void 선택한_기간과_축제_기간이_겹치면_걸린다() {
        // 예정 축제(+10일 ~ +12일)의 시작 하루 전부터 시작일까지 → 하루 겹친다.
        FestivalListResponse response = list(
                today.plusDays(9), today.plusDays(10), FestivalProgressFilter.ALL, null);

        assertThat(ids(response)).containsExactly(UPCOMING);
    }

    @Test
    void 선택한_기간이_축제_기간과_하루도_겹치지_않으면_빠진다() {
        FestivalListResponse response = list(
                today.plusDays(30), today.plusDays(40), FestivalProgressFilter.ALL, null);

        assertThat(ids(response)).isEmpty();
    }

    @Test
    void 기간은_한쪽만_넘겨도_된다() {
        // 종료일만 넘기면 "그 날짜 전에 시작하는 축제"가 된다.
        FestivalListResponse response = list(
                null, today.minusDays(6), FestivalProgressFilter.ALL, null);

        assertThat(ids(response)).containsExactlyInAnyOrder(ENDED_BY_DATE, ENDED_BY_STATUS);
    }

    @Test
    void 좋아요_많은_순은_찜_수_내림차순이다() {
        insertBookmark(9_430_001L, MEMBER, ENDED_BY_DATE);
        insertBookmark(9_430_002L, OTHER_MEMBER, ENDED_BY_DATE);
        insertBookmark(9_430_003L, MEMBER, ONGOING);

        FestivalListResponse response = list(
                null, null, FestivalProgressFilter.ALL, FestivalListSort.BOOKMARK_COUNT_DESC);

        assertThat(ids(response)).startsWith(ENDED_BY_DATE, ONGOING);
        assertThat(item(response, ENDED_BY_DATE).bookmarkCount()).isEqualTo(2);
        assertThat(item(response, ONGOING).bookmarkCount()).isEqualTo(1);
        assertThat(item(response, UPCOMING).bookmarkCount()).isZero();
    }

    @Test
    void 후기_많은_순은_공개_댓글_수_내림차순이고_숨김_삭제_댓글은_세지_않는다() {
        insertComment(9_440_001L, MEMBER, ONGOING, "VISIBLE");
        insertComment(9_440_002L, OTHER_MEMBER, ONGOING, "VISIBLE");
        insertComment(9_440_003L, MEMBER, UPCOMING, "VISIBLE");
        insertComment(9_440_004L, OTHER_MEMBER, UPCOMING, "HIDDEN");
        insertComment(9_440_005L, MEMBER, ENDED_BY_DATE, "DELETED");

        FestivalListResponse response = list(
                null, null, FestivalProgressFilter.ALL, FestivalListSort.COMMENT_COUNT_DESC);

        assertThat(ids(response)).startsWith(ONGOING, UPCOMING);
        assertThat(item(response, ONGOING).commentCount()).isEqualTo(2);
        assertThat(item(response, UPCOMING).commentCount()).isEqualTo(1);
        // 숨김·삭제만 달린 축제는 0이어야 한다.
        assertThat(item(response, ENDED_BY_DATE).commentCount()).isZero();
    }

    @Test
    void 목록은_내가_찜한_축제만_bookmarkedByMe로_표시한다() {
        // 목록에서 바로 찜을 토글하므로 하트를 채울지 판단할 값이 필요하다. 남이 찜한 것은
        // 개수에만 반영되고 내 하트는 비어 있어야 한다.
        insertBookmark(9_430_001L, MEMBER, ONGOING);
        insertBookmark(9_430_002L, OTHER_MEMBER, UPCOMING);

        FestivalListResponse response = list(
                null, null, FestivalProgressFilter.ALL, null, MEMBER);

        assertThat(item(response, ONGOING).bookmarkedByMe()).isTrue();
        assertThat(item(response, UPCOMING).bookmarkedByMe()).isFalse();
        assertThat(item(response, UPCOMING).bookmarkCount()).isEqualTo(1);
        assertThat(response.viewerLoggedIn()).isTrue();
    }

    @Test
    void 비로그인_목록은_찜_상태가_모두_false이고_예외를_던지지_않는다() {
        // 공개 조회다. 여기서 401을 내면 탐색 화면을 열기만 해도 로그인으로 튕긴다(docs/27 2.1).
        insertBookmark(9_430_001L, MEMBER, ONGOING);

        FestivalListResponse response = list(null, null, FestivalProgressFilter.ALL, null, null);

        assertThat(response.items()).isNotEmpty();
        assertThat(response.items()).allSatisfy(item ->
                assertThat(item.bookmarkedByMe()).isFalse());
        // 찜 수는 비로그인에게도 그대로 보인다.
        assertThat(item(response, ONGOING).bookmarkCount()).isEqualTo(1);
        assertThat(response.viewerLoggedIn()).isFalse();
    }

    private FestivalListResponse list(
            LocalDate startDate,
            LocalDate endDate,
            FestivalProgressFilter progress,
            FestivalListSort sort
    ) {
        return list(startDate, endDate, progress, sort, null);
    }

    private FestivalListResponse list(
            LocalDate startDate,
            LocalDate endDate,
            FestivalProgressFilter progress,
            FestivalListSort sort,
            Long viewerMemberId
    ) {
        return festivals.getActiveFestivals(
                0, 50, KEYWORD, null, sort, startDate, endDate, progress, false, viewerMemberId);
    }

    private static java.util.List<Long> ids(FestivalListResponse response) {
        return response.items().stream().map(FestivalListItemResponse::id).toList();
    }

    private static FestivalListItemResponse item(FestivalListResponse response, long festivalId) {
        return response.items().stream()
                .filter(candidate -> candidate.id() == festivalId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("목록에 " + festivalId + "이 없습니다."));
    }

    private void insertMember(long id, String providerUserId) {
        jdbc.update("""
                INSERT INTO members (id, provider, provider_user_id, nickname, status, created_at, updated_at)
                VALUES (?, 'KAKAO', ?, ?, 'ACTIVE', now(), now())
                """, id, providerUserId, providerUserId);
    }

    private void insertFestival(
            long id,
            String contentId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String status
    ) {
        jdbc.update("""
                INSERT INTO festivals (id, content_id, content_type_id, title, address, status,
                                       event_start_date, event_end_date, map_x, map_y,
                                       created_at, updated_at)
                VALUES (?, ?, '15', ?, '강원 어딘가', ?, ?, ?, 128.1, 37.1, now(), now())
                """, id, contentId, title, status, startDate, endDate);
    }

    private void insertBookmark(long id, long memberId, long festivalId) {
        jdbc.update("""
                INSERT INTO content_bookmarks (id, member_id, festival_id, created_at)
                VALUES (?, ?, ?, now())
                """, id, memberId, festivalId);
    }

    private void insertComment(long id, long memberId, long festivalId, String status) {
        jdbc.update("""
                INSERT INTO content_comments (id, member_id, festival_id, body, status, deleted_at,
                                              created_at, updated_at)
                VALUES (?, ?, ?, '후기', ?, CASE WHEN ? = 'VISIBLE' THEN NULL ELSE now() END,
                        now(), now())
                """, id, memberId, festivalId, status, status);
    }
}
