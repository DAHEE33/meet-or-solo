package com.survey.meetorsolo.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.content.comment.service.ContentCommentService;
import com.survey.meetorsolo.domain.content.support.ContentTarget;
import com.survey.meetorsolo.domain.content.bookmark.service.ContentBookmarkService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 찜·댓글·좋아요의 동시성·멱등성·공개 접근 계약 검증
 * (docs/27_CONTENT_BOOKMARK_COMMENT_DESIGN.md 10절 1~6번).
 *
 * <p>실제 PostgreSQL이 필요하다 — {@code ON CONFLICT DO NOTHING}, partial unique index,
 * {@code like_count = like_count ± 1} row lock이 검증 대상이라 in-memory DB로는 의미가 없다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=content-bookmark-comment-integration-test-secret",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@Sql(scripts = {"/fixtures/matching-engine-cleanup.sql", "/fixtures/matching-engine-foundation.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class ContentBookmarkCommentIntegrationTest {

    private static final long ME = 9_110_001L;
    private static final long OTHER = 9_110_002L;
    private static final long THIRD = 9_110_003L;
    private static final long FESTIVAL_ID = 9_100_001L;
    private static final long OTHER_FESTIVAL_ID = 9_100_002L;
    private static final long TOUR_PLACE_ID = 9_140_001L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbc;
    @Autowired ContentCommentService commentService;
    @Autowired ContentBookmarkService bookmarkService;

    @BeforeEach
    void seedTourPlace() {
        // tour_places는 fixture cleanup의 TRUNCATE 대상이 아니므로 직접 정리하고 넣는다.
        // 이 시점에 content_* 테이블은 members/festivals CASCADE로 이미 비어 있다.
        jdbc.update("DELETE FROM tour_places WHERE id = ?", TOUR_PLACE_ID);
        jdbc.update("""
                INSERT INTO tour_places (id, content_id, content_type_id, title, address,
                                         map_x, map_y, status, created_at, updated_at)
                VALUES (?, 'content-fixture-place', '12', '찜 테스트 관광지', '강원 테스트로 9',
                        128.3, 37.3, 'ACTIVE', now(), now())
                """, TOUR_PLACE_ID);
    }

    // === docs/27 10-1: 좋아요 카운터 ==========================================================

    @Test
    void 같은_회원이_좋아요를_반복해도_카운터는_1이다() throws Exception {
        long commentId = insertComment(OTHER, "좋아요 대상");

        toggleLike(ME, commentId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        toggleLike(ME, commentId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));

        assertThat(likeCount(commentId)).isEqualTo(1);
        assertThat(likeRows(commentId)).isEqualTo(1);
    }

    @Test
    void 동시_좋아요_요청은_모두_성공하고_카운터는_1이다() throws Exception {
        long commentId = insertComment(OTHER, "동시 좋아요 대상");

        runConcurrently(6, () -> commentService.toggleLike(ME, commentId, true));

        assertThat(likeRows(commentId)).isEqualTo(1);
        assertThat(likeCount(commentId)).isEqualTo(1);
    }

    @Test
    void 서로_다른_회원의_동시_좋아요는_각각_반영된다() throws Exception {
        long commentId = insertComment(THIRD, "여러 명 좋아요 대상");

        runConcurrently(2, index -> commentService.toggleLike(index == 0 ? ME : OTHER, commentId, true));

        assertThat(likeRows(commentId)).isEqualTo(2);
        assertThat(likeCount(commentId)).isEqualTo(2);
    }

    @Test
    void 좋아요_해제_후_다시_누르면_카운터는_1이다() throws Exception {
        long commentId = insertComment(OTHER, "토글 대상");

        toggleLike(ME, commentId, true).andExpect(jsonPath("$.data.likeCount").value(1));
        toggleLike(ME, commentId, false).andExpect(jsonPath("$.data.likeCount").value(0));
        toggleLike(ME, commentId, true).andExpect(jsonPath("$.data.likeCount").value(1));

        assertThat(likeCount(commentId)).isEqualTo(1);
    }

    @Test
    void 누른_적_없는_좋아요를_두_번_해제해도_카운터는_0에서_음수로_내려가지_않는다() throws Exception {
        long commentId = insertComment(OTHER, "음수 방지 대상");

        toggleLike(ME, commentId, false).andExpect(jsonPath("$.data.likeCount").value(0));
        toggleLike(ME, commentId, false).andExpect(jsonPath("$.data.likeCount").value(0));

        assertThat(likeCount(commentId)).isZero();
        assertThat(likeRows(commentId)).isZero();
    }

    // === docs/27 10-2: 찜 멱등 ================================================================

    @Test
    void 찜을_두_번_등록해도_row는_1건이다() throws Exception {
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(true));
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(true));

        assertThat(bookmarkRows("festival_id", FESTIVAL_ID)).isEqualTo(1);
    }

    @Test
    void 찜을_두_번_해제해도_성공하고_row는_0건이다() throws Exception {
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, true);

        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false));
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, false)
                .andExpect(status().isOk());

        assertThat(bookmarkRows("festival_id", FESTIVAL_ID)).isZero();
    }

    @Test
    void 동시_찜_등록은_모두_성공하고_row는_1건이다() throws Exception {
        runConcurrently(6, () -> bookmarkService.toggle(ME, ContentTarget.festival(FESTIVAL_ID), true));

        assertThat(bookmarkRows("festival_id", FESTIVAL_ID)).isEqualTo(1);
    }

    @Test
    void 축제와_관광지_찜은_서로_독립이다() throws Exception {
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, true).andExpect(status().isOk());
        toggleBookmark(ME, "/api/spots/" + TOUR_PLACE_ID, true).andExpect(status().isOk());

        assertThat(bookmarkRows("festival_id", FESTIVAL_ID)).isEqualTo(1);
        assertThat(bookmarkRows("tour_place_id", TOUR_PLACE_ID)).isEqualTo(1);
    }

    @Test
    void HIDDEN_대상은_찜할_수_없다() throws Exception {
        jdbc.update("UPDATE festivals SET status='HIDDEN' WHERE id=?", OTHER_FESTIVAL_ID);

        toggleBookmark(ME, "/api/festivals/" + OTHER_FESTIVAL_ID, true)
                .andExpect(status().isNotFound());
    }

    @Test
    void 내_찜_목록은_기존_목록_DTO를_그대로_품고_HIDDEN만_제외한다() throws Exception {
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, true);
        toggleBookmark(ME, "/api/festivals/" + OTHER_FESTIVAL_ID, true);
        jdbc.update("UPDATE festivals SET status='HIDDEN' WHERE id=?", OTHER_FESTIVAL_ID);

        mockMvc.perform(get("/api/members/me/bookmarks").param("type", "FESTIVAL").cookie(cookie(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].targetType").value("FESTIVAL"))
                .andExpect(jsonPath("$.data.items[0].bookmarkedAt").exists())
                .andExpect(jsonPath("$.data.items[0].tourPlace").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].festival.id").value(FESTIVAL_ID))
                .andExpect(jsonPath("$.data.items[0].festival.contentId").value("fixture-festival-1"))
                .andExpect(jsonPath("$.data.items[0].festival.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].festival.eventStartDate").exists())
                .andExpect(jsonPath("$.data.items[0].festival.mapX").exists());
    }

    @Test
    void 종료된_축제도_찜_목록에_남는다() throws Exception {
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, true);
        jdbc.update("UPDATE festivals SET status='ENDED' WHERE id=?", FESTIVAL_ID);

        mockMvc.perform(get("/api/members/me/bookmarks").param("type", "FESTIVAL").cookie(cookie(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].festival.status").value("ENDED"));
    }

    @Test
    void 관광지_찜_목록은_관광지_목록_DTO를_품는다() throws Exception {
        toggleBookmark(ME, "/api/spots/" + TOUR_PLACE_ID, true);

        mockMvc.perform(get("/api/members/me/bookmarks").param("type", "TOUR_PLACE").cookie(cookie(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].targetType").value("TOUR_PLACE"))
                .andExpect(jsonPath("$.data.items[0].festival").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].tourPlace.id").value(TOUR_PLACE_ID))
                .andExpect(jsonPath("$.data.items[0].tourPlace.contentTypeId").value("12"));
    }

    @Test
    void 내_찜_목록은_인증을_요구한다() throws Exception {
        mockMvc.perform(get("/api/members/me/bookmarks")).andExpect(status().isUnauthorized());
    }

    // === docs/27 10-3: 삭제된 댓글 ============================================================

    @Test
    void 삭제된_댓글은_목록에서_빠지고_좋아요도_할_수_없다() throws Exception {
        long commentId = insertComment(ME, "곧 삭제될 댓글");
        long survivingId = insertComment(OTHER, "남아있을 댓글");

        mockMvc.perform(delete("/api/comments/{id}", commentId).cookie(cookie(ME)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/festivals/{id}/comments", FESTIVAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(survivingId));

        toggleLike(OTHER, commentId, true).andExpect(status().isNotFound());
        assertThat(commentStatus(commentId)).isEqualTo("DELETED");
    }

    @Test
    void 본인_댓글을_두_번_삭제해도_204다() throws Exception {
        long commentId = insertComment(ME, "멱등 삭제 대상");

        mockMvc.perform(delete("/api/comments/{id}", commentId).cookie(cookie(ME)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/comments/{id}", commentId).cookie(cookie(ME)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    // === docs/27 10-4: 비로그인 접근 (2.1절 회귀 방지) ========================================

    @Test
    void 비로그인_댓글_목록은_200이고_likedByMe와_mine이_false다() throws Exception {
        long commentId = insertComment(OTHER, "공개 댓글");
        commentService.toggleLike(ME, commentId, true);

        mockMvc.perform(get("/api/festivals/{id}/comments", FESTIVAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(false))
                .andExpect(jsonPath("$.data.items[0].mine").value(false))
                .andExpect(jsonPath("$.data.items[0].nickname").value("fixture" + OTHER))
                .andExpect(jsonPath("$.data.items[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].profileImageUrl").doesNotExist());
    }

    @Test
    void 만료되거나_위조된_쿠키로도_공개_조회는_200이다() throws Exception {
        insertComment(OTHER, "공개 댓글");
        var tampered = new jakarta.servlet.http.Cookie("access_token", "not-a-valid-jwt");

        mockMvc.perform(get("/api/festivals/{id}/comments", FESTIVAL_ID).cookie(tampered))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(false));
        mockMvc.perform(get("/api/festivals/{id}/engagement", FESTIVAL_ID).cookie(tampered))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewer.loggedIn").value(false));
    }

    @Test
    void 비로그인_engagement는_200이고_찜은_false다() throws Exception {
        insertComment(OTHER, "댓글 하나");

        mockMvc.perform(get("/api/festivals/{id}/engagement", FESTIVAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.viewer.loggedIn").value(false))
                .andExpect(jsonPath("$.data.viewer.admin").value(false));

        mockMvc.perform(get("/api/spots/{id}/engagement", TOUR_PLACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(0));
    }

    @Test
    void 로그인_engagement는_내_찜_상태와_관리자_여부를_알려준다() throws Exception {
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, true);
        jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", OTHER);

        mockMvc.perform(get("/api/festivals/{id}/engagement", FESTIVAL_ID).cookie(cookie(ME)))
                .andExpect(jsonPath("$.data.bookmarked").value(true))
                .andExpect(jsonPath("$.data.viewer.loggedIn").value(true))
                .andExpect(jsonPath("$.data.viewer.admin").value(false));

        mockMvc.perform(get("/api/festivals/{id}/engagement", FESTIVAL_ID).cookie(cookie(OTHER)))
                .andExpect(jsonPath("$.data.bookmarked").value(false))
                .andExpect(jsonPath("$.data.viewer.admin").value(true));
    }

    // === docs/27 10-5: 삭제 권한 ==============================================================

    @Test
    void 남의_댓글은_삭제할_수_없고_원본이_남는다() throws Exception {
        long commentId = insertComment(OTHER, "남의 댓글");

        mockMvc.perform(delete("/api/comments/{id}", commentId).cookie(cookie(ME)))
                .andExpect(status().isForbidden());

        assertThat(commentStatus(commentId)).isEqualTo("VISIBLE");
    }

    @Test
    void 없는_댓글_삭제는_404다() throws Exception {
        mockMvc.perform(delete("/api/comments/{id}", 9_199_999L).cookie(cookie(ME)))
                .andExpect(status().isNotFound());
    }

    // === docs/27 10-6: 탈퇴 연동 ==============================================================

    @Test
    void 탈퇴하면_그_회원의_공개_댓글만_일괄_숨겨진다() throws Exception {
        long mine = insertComment(ME, "내 댓글");
        long others = insertComment(OTHER, "남의 댓글");

        assertThat(commentService.softDeleteAllOnWithdrawal(ME)).isEqualTo(1);

        assertThat(commentStatus(mine)).isEqualTo("DELETED");
        assertThat(commentStatus(others)).isEqualTo("VISIBLE");
        mockMvc.perform(get("/api/festivals/{id}/comments", FESTIVAL_ID))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(others));
    }

    @Test
    void 탈퇴하면_찜은_물리_삭제된다() throws Exception {
        toggleBookmark(ME, "/api/festivals/" + FESTIVAL_ID, true);
        toggleBookmark(OTHER, "/api/festivals/" + FESTIVAL_ID, true);

        assertThat(bookmarkService.deleteAllOnWithdrawal(ME)).isEqualTo(1);

        assertThat(bookmarkRows("festival_id", FESTIVAL_ID)).isZero();
        assertThat(Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM content_bookmarks WHERE member_id=?)",
                Boolean.class, OTHER))).isTrue();
    }

    // === 관리자 숨김 (docs/27 5.6) =============================================================

    @Test
    void 관리자는_댓글을_숨기고_다시_공개할_수_있다() throws Exception {
        long commentId = insertComment(OTHER, "숨김 대상");
        jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", THIRD);

        changeVisibility(THIRD, commentId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(false));
        assertThat(commentStatus(commentId)).isEqualTo("HIDDEN");
        mockMvc.perform(get("/api/festivals/{id}/comments", FESTIVAL_ID))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        changeVisibility(THIRD, commentId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(true));
        assertThat(commentStatus(commentId)).isEqualTo("VISIBLE");
    }

    @Test
    void 관리자가_아니면_숨김을_거절한다() throws Exception {
        long commentId = insertComment(OTHER, "숨김 대상");

        changeVisibility(ME, commentId, false).andExpect(status().isForbidden());

        assertThat(commentStatus(commentId)).isEqualTo("VISIBLE");
    }

    @Test
    void 작성자가_삭제한_댓글은_관리자가_되살리지_않는다() throws Exception {
        long commentId = insertComment(ME, "작성자 삭제 대상");
        jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", THIRD);
        mockMvc.perform(delete("/api/comments/{id}", commentId).cookie(cookie(ME)))
                .andExpect(status().isNoContent());

        changeVisibility(THIRD, commentId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false));

        assertThat(commentStatus(commentId)).isEqualTo("DELETED");
    }

    // === 헬퍼 =================================================================================

    private org.springframework.test.web.servlet.ResultActions toggleLike(
            long memberId, long commentId, boolean liked) throws Exception {
        return mockMvc.perform(put("/api/comments/{id}/like", commentId)
                .cookie(cookie(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"liked\":" + liked + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions toggleBookmark(
            long memberId, String basePath, boolean bookmarked) throws Exception {
        return mockMvc.perform(put(basePath + "/bookmark")
                .cookie(cookie(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookmarked\":" + bookmarked + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions changeVisibility(
            long adminMemberId, long commentId, boolean visible) throws Exception {
        return mockMvc.perform(put("/api/admin/comments/{id}/visibility", commentId)
                .cookie(cookie(adminMemberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":" + visible + "}"));
    }

    /**
     * 댓글을 직접 넣는다. API로 여러 건을 연속 등록하면 도배 완화 5초 규칙(docs/27 5.2)에
     * 걸리므로, 규칙 자체를 검증하는 단위 테스트와 분리해 여기서는 DB에 바로 넣는다.
     */
    private long insertComment(long memberId, String body) {
        return jdbc.queryForObject("""
                INSERT INTO content_comments (member_id, festival_id, body, like_count, status,
                                              created_at, updated_at)
                VALUES (?, ?, ?, 0, 'VISIBLE', ?, ?)
                RETURNING id
                """, Long.class, memberId, FESTIVAL_ID, body,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private int likeCount(long commentId) {
        return jdbc.queryForObject(
                "SELECT like_count FROM content_comments WHERE id=?", Integer.class, commentId);
    }

    private int likeRows(long commentId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM content_comment_likes WHERE comment_id=?", Integer.class, commentId);
    }

    private String commentStatus(long commentId) {
        return jdbc.queryForObject(
                "SELECT status FROM content_comments WHERE id=?", String.class, commentId);
    }

    private int bookmarkRows(String column, long targetId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM content_bookmarks WHERE member_id=? AND " + column + "=?",
                Integer.class, ME, targetId);
    }

    private void runConcurrently(int workerCount, Runnable task) throws Exception {
        runConcurrently(workerCount, index -> task.run());
    }

    private void runConcurrently(int workerCount, java.util.function.IntConsumer task) throws Exception {
        var executor = Executors.newFixedThreadPool(workerCount);
        var ready = new CountDownLatch(workerCount);
        var start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workerCount; index++) {
                int worker = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timeout");
                    }
                    task.accept(worker);
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Void> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isNull();
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private jakarta.servlet.http.Cookie cookie(long memberId) {
        return new jakarta.servlet.http.Cookie(
                "access_token", jwtProvider.createAccessToken(memberId, "ACTIVE"));
    }
}
