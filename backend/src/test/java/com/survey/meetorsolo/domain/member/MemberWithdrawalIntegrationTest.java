package com.survey.meetorsolo.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberActionReasonCode;
import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberForcedWithdrawalRequest;
import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberStatus;
import com.survey.meetorsolo.domain.admin.member.service.AdminMemberService;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.member.service.MemberWithdrawalService;
import com.survey.meetorsolo.domain.safety.block.service.MemberBlockService;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 회원 탈퇴와 관리자 강제 탈퇴({@code docs/19} 4.4)를 실제 PostgreSQL로 검증한다.
 *
 * <p>{@code V28}의 CHECK 제약이 익명화 누락과 스냅샷 잔존을 실제로 거부하는지,
 * 그리고 탈퇴 이후 다른 사용자 화면에서 개인정보가 복원되지 않는지를 함께 본다.
 *
 * <p>닉네임은 컬럼에 저장하지 않고 조회 SQL이 {@code status}로 표시 문구를 만든다
 * ({@code V30}). 그 치환이 실제로 동작하는지와, {@code V30}의 CHECK가 문구를 컬럼에
 * 되돌려 넣지 못하게 막는지도 이 클래스에서 본다.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.jwt.secret=member-withdrawal-integration-test-secret",
        "app.admin.report.cursor-hmac-secret=member-withdrawal-cursor-secret-32-bytes",
        "app.admin.member.suspension-scheduler-enabled=false",
        "app.support.contact-email=support@example.test",
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
@Import(MemberWithdrawalIntegrationTest.FixedClockConfiguration.class)
class MemberWithdrawalIntegrationTest {

    private static final long ADMIN = 9_820_001L;
    private static final long USER = 9_820_002L;
    private static final long OTHER = 9_820_003L;
    private static final long FESTIVAL = 9_821_001L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-09T12:00:00+09:00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired MemberWithdrawalService withdrawal;
    @Autowired AdminMemberService adminMembers;
    @Autowired MemberBlockService blocks;
    @Autowired MatchGroupMemberRepository groupMembers;
    @Autowired MatchEventRepository matchEvents;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        // 축제를 지우기 전에 매칭 자식 row를 먼저 지운다. TRUNCATE members CASCADE는
        // match_attempts와 match_groups를 지우지 않으므로 festivals 삭제가 FK 위반으로 실패하고,
        // 그러면 @BeforeEach가 죽어 이 클래스의 모든 테스트가 함께 깨진다.
        jdbc.update("TRUNCATE TABLE members RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM match_events");
        jdbc.update("DELETE FROM match_group_members");
        jdbc.update("DELETE FROM match_groups");
        jdbc.update("DELETE FROM match_proposals");
        jdbc.update("DELETE FROM match_pools");
        jdbc.update("DELETE FROM match_attempts");
        jdbc.update("DELETE FROM festival_checkins");
        jdbc.update("DELETE FROM festivals WHERE id=?", FESTIVAL);
        insertMember(ADMIN, "admin", "ADMIN", "ACTIVE");
        insertMember(USER, "탈퇴대상", "USER", "ACTIVE");
        insertMember(OTHER, "남은회원", "USER", "ACTIVE");
        jdbc.update("""
                INSERT INTO festivals(id, content_id, content_type_id, title, status, created_at, updated_at)
                VALUES (?, 'withdrawal-festival', '15', '탈퇴 테스트 축제', 'ACTIVE', ?, ?)
                """, FESTIVAL, NOW, NOW);
    }

    @Test
    void 본인_탈퇴는_개인정보를_익명화하고_상태를_WITHDRAWN으로_바꾼다() {
        jdbc.update("""
                UPDATE members SET email=?, intro=?, profile_image_url=?, profile_image_object_key=?,
                       gender_encrypted=?, age_range_encrypted=? WHERE id=?
                """, "user@example.test", "소개글", "https://image.test/a.png", "profile/9820002",
                new byte[]{1, 2}, new byte[]{3, 4}, USER);

        withdrawal.withdrawSelf(USER);

        assertThat(status(USER)).isEqualTo("WITHDRAWN");
        // 컬럼에는 표시 문구를 저장하지 않는다. V30의 CHECK가 저장 자체를 거부한다.
        assertThat(nickname(USER)).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT email IS NULL AND intro IS NULL AND profile_image_url IS NULL
                       AND profile_image_object_key IS NULL AND gender_encrypted IS NULL
                       AND age_range_encrypted IS NULL
                  FROM members WHERE id=?
                """, Boolean.class, USER)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT withdrawn_from_status FROM members WHERE id=?", String.class, USER))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT withdrawn_rejoin_blocked FROM members WHERE id=?", Boolean.class, USER))
                .isFalse();
    }

    @Test
    void 반복_탈퇴_요청은_멱등하다() {
        withdrawal.withdrawSelf(USER);
        withdrawal.withdrawSelf(USER);
        withdrawal.withdrawSelf(USER);

        assertThat(status(USER)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject(
                "SELECT withdrawn_at FROM members WHERE id=?", OffsetDateTime.class, USER))
                .isEqualTo(NOW);
    }

    @Test
    void 탈퇴는_취향과_임베딩과_찜을_물리_삭제하고_댓글을_숨긴다() {
        jdbc.update("INSERT INTO member_travel_styles(member_id, style_code) VALUES (?, 'FOOD')", USER);
        jdbc.update("""
                INSERT INTO member_preference_embeddings(member_id, preference_text, embedding_status)
                VALUES (?, '조용한 축제를 좋아해요', 'PENDING')
                """, USER);
        jdbc.update("INSERT INTO content_bookmarks(member_id, festival_id, created_at) VALUES (?, ?, ?)",
                USER, FESTIVAL, NOW);
        jdbc.update("""
                INSERT INTO content_comments(member_id, festival_id, body, status, created_at, updated_at)
                VALUES (?, ?, '댓글 본문', 'VISIBLE', ?, ?)
                """, USER, FESTIVAL, NOW, NOW);

        withdrawal.withdrawSelf(USER);

        assertThat(count("member_travel_styles", "member_id", USER)).isZero();
        assertThat(count("member_preference_embeddings", "member_id", USER)).isZero();
        assertThat(count("content_bookmarks", "member_id", USER)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM content_comments WHERE member_id=?", String.class, USER))
                .isEqualTo("DELETED");
    }

    /**
     * 동의 row는 남긴다. "동의를 받았다"는 사실은 개인정보 처리 근거의 증빙이고,
     * {@code revoked_at}으로 근거 종료만 기록한다.
     */
    @Test
    void 탈퇴는_동의를_철회하되_기록은_남긴다() {
        jdbc.update("""
                INSERT INTO member_consents(member_id, consent_type, version, agreed, agreed_at)
                VALUES (?, 'TERMS', 'v1', TRUE, ?), (?, 'PRIVACY', 'v1', TRUE, ?)
                """, USER, NOW.minusDays(1), USER, NOW.minusDays(1));

        withdrawal.withdrawSelf(USER);

        assertThat(count("member_consents", "member_id", USER)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM member_consents
                 WHERE member_id=? AND agreed = TRUE AND revoked_at IS NULL
                """, Long.class, USER)).isZero();
    }

    /** 감사 추적이 끊기면 신고·제재 이력을 되짚을 수 없다. 물리 삭제하지 않는지 확인한다. */
    @Test
    void 탈퇴해도_신고_감사_이력은_남는다() {
        long reportId = jdbc.queryForObject("""
                INSERT INTO reports(reporter_member_id, reported_member_id, reason_code, status,
                    created_at, updated_at)
                VALUES (?, ?, 'SAFETY', 'RESOLVED', ?, ?) RETURNING id
                """, Long.class, OTHER, USER, NOW.minusDays(2), NOW.minusDays(2));

        withdrawal.withdrawSelf(USER);

        assertThat(jdbc.queryForObject(
                "SELECT reported_member_id FROM reports WHERE id=?", Long.class, reportId))
                .isEqualTo(USER);
        assertThat(count("members", "id", USER)).isOne();
    }

    /**
     * 탈퇴 이후 다른 사용자 화면에서 개인정보가 복원되지 않는지 본다.
     * 닉네임을 읽는 경로가 {@code members}를 join하므로 고정 문구로 덮는 것만으로 커버된다.
     */
    @Test
    void 탈퇴_회원은_다른_사용자_화면에서_닉네임이_복원되지_않는다() {
        jdbc.update("""
                INSERT INTO user_blocks(blocker_member_id, blocked_member_id, created_at)
                VALUES (?, ?, ?)
                """, OTHER, USER, NOW.minusDays(1));

        withdrawal.withdrawSelf(USER);

        assertThat(blocks.getMyBlocks(OTHER))
                .singleElement()
                .satisfies(block -> {
                    // 컬럼은 NULL이고 이 문구는 MemberBlockRepository의 CASE가 만든다.
                    // null이 그대로 내려가면 프론트가 nickname.slice(0, 1)에서 죽는다.
                    assertThat(block.nickname()).isEqualTo("탈퇴한 회원");
                    assertThat(block.nickname()).doesNotContain("탈퇴대상");
                    assertThat(block.profileImageUrl()).isNull();
                });
    }

    @Test
    void 탈퇴는_활성_체크인과_대기중_pool을_정리한다() {
        insertCheckinAndPool("WAITING");

        withdrawal.withdrawSelf(USER);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM festival_checkins WHERE member_id=?", String.class, USER))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM match_pools WHERE member_id=?", String.class, USER))
                .isEqualTo("CANCELLED");
        // 탈퇴에는 penalty와 cooldown을 매기지 않는다. 부과 대상이 익명화되므로 의미가 없다.
        assertThat(count("match_cooldowns", "member_id", USER)).isZero();
        assertThat(count("match_penalty_events", "member_id", USER)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT penalty_score FROM members WHERE id=?", Integer.class, USER)).isZero();
    }

    /**
     * 2인 그룹에서 한 명이 탈퇴하면 남은 한 명으로는 만남이 성립하지 않는다.
     * {@code MatchGroupContinuationPolicy}가 그룹을 취소하고 남은 사람도 이탈 처리해야 한다.
     */
    @Test
    void 진행_중_2인_그룹에서_탈퇴하면_그룹이_취소되고_남은_사람도_정리된다() {
        insertConfirmedGroup(false);

        withdrawal.withdrawSelf(USER);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM match_groups WHERE id=?", String.class, 9824001L))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT cancel_reason FROM match_groups WHERE id=?", String.class, 9824001L))
                .isEqualTo("INSUFFICIENT_ACTIVE_MEMBERS");
        assertThat(groupMemberStatus(USER)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT cancel_reason FROM match_group_members WHERE member_id=?", String.class, USER))
                .isEqualTo("WITHDRAWN");
        // 남은 사람은 취소가 아니라 이탈로 정리된다. 본인이 취소한 것이 아니다.
        assertThat(groupMemberStatus(OTHER)).isEqualTo("LEFT");
    }

    /** 남은 사람이 상황을 알 수 있어야 한다. 기존 이벤트 타입을 그대로 쓴다. */
    @Test
    void 그룹_탈퇴는_기존_이벤트_타입으로_남은_사람에게_알린다() {
        insertConfirmedGroup(false);

        withdrawal.withdrawSelf(USER);

        assertThat(jdbc.queryForList("""
                SELECT event_type FROM match_events WHERE group_id=? ORDER BY id
                """, String.class, 9824001L))
                .containsExactly("MEMBER_CANCELLED", "MATCH_CANCELLED");
    }

    /**
     * 탈퇴는 자발적 이탈이지만 penalty를 매기지 않는다. 부과 대상이 익명화되므로 의미가 없고,
     * penalty를 매기려고 기존 취소 서비스를 재사용하면 도착 마감이 지난 시점에 탈퇴가 실패한다.
     */
    @Test
    void 그룹_탈퇴에도_penalty를_부과하지_않는다() {
        insertConfirmedGroup(false);

        withdrawal.withdrawSelf(USER);

        assertThat(count("match_penalty_events", "member_id", USER)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT penalty_score FROM members WHERE id=?", Integer.class, USER)).isZero();
    }

    /**
     * 도착 마감이 지난 그룹에서도 탈퇴는 성공해야 한다.
     * {@code MatchCancellationService.cancel}은 이 시점에 예외를 던지므로 재사용할 수 없다.
     */
    @Test
    void 도착_마감이_지난_그룹에서도_탈퇴는_성공한다() {
        insertConfirmedGroup(true);

        withdrawal.withdrawSelf(USER);

        assertThat(status(USER)).isEqualTo("WITHDRAWN");
        assertThat(groupMemberStatus(USER)).isEqualTo("CANCELLED");
    }

    /** {@code V28} 제약. 익명화 누락은 코드 리뷰로 놓치기 쉬워 DB가 막아야 한다. */
    @Test
    void 탈퇴_회원에_개인정보를_남길_수_없다() {
        withdrawal.withdrawSelf(USER);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE members SET email='back@example.test' WHERE id=?", USER))
                .hasMessageContaining("chk_members_withdrawn_anonymized");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE members SET intro='되살린 소개' WHERE id=?", USER))
                .hasMessageContaining("chk_members_withdrawn_anonymized");
    }

    /** {@code V28} 제약. 재가입에서 스냅샷을 지우는 것을 잊으면 여기서 걸린다. */
    @Test
    void 탈퇴_상태가_아닌_회원에_탈퇴_스냅샷을_남길_수_없다() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE members SET withdrawn_from_status='ACTIVE' WHERE id=?", USER))
                .hasMessageContaining("chk_members_withdrawal_snapshot");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE members SET withdrawn_at=? WHERE id=?", NOW, USER))
                .hasMessageContaining("chk_members_withdrawal_snapshot");
    }

    @Test
    void 목록에_없는_탈퇴_시점_상태는_저장할_수_없다() {
        withdrawal.withdrawSelf(USER);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE members SET withdrawn_from_status='NOT_A_STATUS' WHERE id=?", USER))
                .hasMessageContaining("chk_members_withdrawn_from_status");
    }

    @Test
    void 관리자_강제_탈퇴는_감사로그를_남기고_재가입을_차단한다() {
        adminMembers.forceWithdraw(ADMIN, USER, UUID.randomUUID().toString(),
                forcedWithdrawal(null, true));

        assertThat(status(USER)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject(
                "SELECT withdrawn_by_admin FROM members WHERE id=?", Boolean.class, USER)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT withdrawn_rejoin_blocked FROM members WHERE id=?", Boolean.class, USER)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT action_type FROM admin_actions WHERE target_member_id=?", String.class, USER))
                .isEqualTo("FORCED_WITHDRAWAL");
        assertThat(jdbc.queryForObject(
                "SELECT metadata->>'beforeStatus' FROM admin_actions WHERE target_member_id=?",
                String.class, USER)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT metadata->>'blockRejoin' FROM admin_actions WHERE target_member_id=?",
                String.class, USER)).isEqualTo("true");
    }

    /** 로그인이 막힌 회원의 탈퇴 대행은 제재가 아니라 민원 처리다. */
    @Test
    void 강제_탈퇴_대행은_재가입을_차단하지_않는다() {
        adminMembers.forceWithdraw(ADMIN, USER, UUID.randomUUID().toString(),
                forcedWithdrawal(null, false));

        assertThat(jdbc.queryForObject(
                "SELECT withdrawn_by_admin FROM members WHERE id=?", Boolean.class, USER)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT withdrawn_rejoin_blocked FROM members WHERE id=?", Boolean.class, USER)).isFalse();
    }

    /** 값을 주지 않으면 차단한다. 되돌릴 수 없는 조치의 기본값은 보수적으로 둔다. */
    @Test
    void blockRejoin이_없으면_재가입을_차단한다() {
        adminMembers.forceWithdraw(ADMIN, USER, UUID.randomUUID().toString(),
                new AdminMemberForcedWithdrawalRequest(
                        AdminMemberActionReasonCode.FRAUD_OR_SCAM, null,
                        AdminMemberStatus.ACTIVE, null));

        assertThat(jdbc.queryForObject(
                "SELECT withdrawn_rejoin_blocked FROM members WHERE id=?", Boolean.class, USER)).isTrue();
    }

    /** 신고자 보호. 관리자 자유 입력 note는 감사 로그에만 남고 회원 record로 넘어가면 안 된다. */
    @Test
    void 강제_탈퇴의_관리자_메모는_회원_record로_넘어가지_않는다() {
        String internalNote = "신고 3건 누적, 신고자 진술 확인 완료";
        adminMembers.forceWithdraw(ADMIN, USER, UUID.randomUUID().toString(),
                forcedWithdrawal(internalNote, true));

        assertThat(jdbc.queryForObject(
                "SELECT reason FROM admin_actions WHERE target_member_id=?", String.class, USER))
                .isEqualTo(internalNote);
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(nickname, '') || COALESCE(intro, '') || COALESCE(email, '')
                  FROM members WHERE id=?
                """, String.class, USER)).doesNotContain("신고");
    }

    @Test
    void 같은_Idempotency_Key로_강제_탈퇴를_반복하면_한_번만_적용된다() {
        String key = UUID.randomUUID().toString();
        AdminMemberForcedWithdrawalRequest request = forcedWithdrawal(null, true);
        adminMembers.forceWithdraw(ADMIN, USER, key, request);
        adminMembers.forceWithdraw(ADMIN, USER, key, request);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM admin_actions WHERE target_member_id=?", Long.class, USER))
                .isOne();
    }

    @Test
    void 관리자_계정은_강제_탈퇴시킬_수_없다() {
        long otherAdmin = 9_820_004L;
        insertMember(otherAdmin, "다른관리자", "ADMIN", "ACTIVE");

        assertThatThrownBy(() -> adminMembers.forceWithdraw(
                ADMIN, otherAdmin, UUID.randomUUID().toString(), forcedWithdrawal(null, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_MEMBER_STATUS_CONFLICT));
        assertThat(status(otherAdmin)).isEqualTo("ACTIVE");
    }

    /**
     * 제재({@code SUSPEND}/{@code BAN})는 활성 매칭이 있으면 409로 거부하지만, 탈퇴는 정리하고
     * 진행한다. 거부하면 만남이 확정된 회원의 개인정보 삭제 요청을 처리할 수 없다.
     */
    @Test
    void 활성_매칭이_있어도_강제_탈퇴는_진행된다() {
        insertCheckinAndPool("WAITING");

        adminMembers.forceWithdraw(ADMIN, USER, UUID.randomUUID().toString(),
                forcedWithdrawal(null, true));

        assertThat(status(USER)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM match_pools WHERE member_id=?", String.class, USER))
                .isEqualTo("CANCELLED");
    }

    @Test
    void 이미_탈퇴한_회원의_강제_탈퇴는_상태_충돌로_알린다() {
        withdrawal.withdrawSelf(USER);

        assertThatThrownBy(() -> adminMembers.forceWithdraw(
                ADMIN, USER, UUID.randomUUID().toString(), forcedWithdrawal(null, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_MEMBER_STATUS_CONFLICT));
    }

    @Test
    void 강제_탈퇴_메모에_민감정보_표시가_있으면_거부한다() {
        assertThatThrownBy(() -> adminMembers.forceWithdraw(
                ADMIN, USER, UUID.randomUUID().toString(),
                forcedWithdrawal("oauth token 확인", true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_MEMBER_INVALID_REQUEST));
        assertThat(status(USER)).isEqualTo("ACTIVE");
    }

    private AdminMemberForcedWithdrawalRequest forcedWithdrawal(String note, boolean blockRejoin) {
        return new AdminMemberForcedWithdrawalRequest(
                AdminMemberActionReasonCode.FRAUD_OR_SCAM, note, AdminMemberStatus.ACTIVE, blockRejoin);
    }

    /**
     * 매칭 기록은 그룹 상태와 무관하게 함께 있었던 사람을 보여주므로 탈퇴자가 결과에 남는다.
     * 컬럼이 {@code NULL}인데 치환이 없으면 닉네임이 {@code null}로 내려가고, 프론트가
     * {@code nickname.slice(0, 1)}로 첫 글자를 뽑으므로({@code MatchHistoryPage.tsx})
     * 빈 칸이 아니라 렌더링 자체가 죽는다.
     */
    @Test
    void 매칭_기록_조회는_탈퇴_회원_닉네임을_표시_문구로_내려준다() {
        insertConfirmedGroup(false);

        withdrawal.withdrawSelf(USER);

        assertThat(groupMembers.findHistoryMembersByGroupIds(List.of(9824001L), OTHER))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getMemberId()).isEqualTo(USER);
                    assertThat(row.getNickname()).isEqualTo("탈퇴한 회원");
                });
        // 컬럼 자체는 비어 있어야 한다. 문구가 컬럼에 있으면 재가입 시 그대로 살아난다.
        assertThat(nickname(USER)).isNull();
    }

    /**
     * 매칭방 이벤트의 actor는 {@code match_group_members}를 LEFT JOIN하는데 그 join에는
     * 멤버 상태 필터가 없다. 탈퇴자의 group_member row가 {@code CANCELLED}로 남아 있으므로
     * actor 닉네임이 계속 조회된다.
     */
    @Test
    void 매칭_이벤트_actor도_탈퇴_회원_닉네임을_표시_문구로_내려준다() {
        insertConfirmedGroup(false);

        withdrawal.withdrawSelf(USER);

        assertThat(matchEvents.findLatestCurrentGroupEvents(9824001L))
                .filteredOn(event -> "MEMBER_CANCELLED".equals(event.getEventType()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getActorMemberId()).isEqualTo(USER);
                    assertThat(event.getActorNickname()).isEqualTo("탈퇴한 회원");
                });
    }

    /**
     * 살아 있는 회원이 표시 문구를 닉네임으로 갖지 못하게 DB가 막는다({@code V30}).
     *
     * <p>{@code MemberProfileService.requireSelectableNickname}이 앞단에서 막지만, 그 검사를
     * 지나가는 새 경로가 생기면 살아 있는 계정이 탈퇴한 회원으로 위장한다. 여기서 걸려야 한다.
     */
    @Test
    void 살아_있는_회원은_표시_문구를_닉네임으로_가질_수_없다() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE members SET nickname='탈퇴한 회원' WHERE id=?", USER))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_members_nickname_not_withdrawn_label");
    }

    /** 탈퇴 회원에게 닉네임이 남아 있으면 익명화 누락이다({@code V30}이 검사 대상에 넣었다). */
    @Test
    void 탈퇴_회원에게_닉네임이_남아_있으면_거부된다() {
        withdrawal.withdrawSelf(USER);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE members SET nickname='되살아난닉네임' WHERE id=?", USER))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_members_withdrawn_anonymized");
    }

    /**
     * 확정된 2인 그룹을 만든다.
     *
     * @param staleConfirmation 도착 마감이 지난 상태로 만들지. 기존 취소 서비스가 거부하는 조건이다.
     */
    private void insertConfirmedGroup(boolean staleConfirmation) {
        OffsetDateTime confirmedAt = staleConfirmation ? NOW.minusDays(2) : NOW.minusMinutes(10);
        jdbc.update("""
                INSERT INTO match_attempts(id, festival_id, target_group_size, status, score,
                    created_by, started_at, expires_at, confirmed_at, created_at, updated_at)
                VALUES (9824000, ?, 2, 'CONFIRMED', 0, 'SCHEDULER', ?, ?, ?, ?, ?)
                """, FESTIVAL, confirmedAt.minusMinutes(1), confirmedAt.plusHours(1),
                confirmedAt, confirmedAt, confirmedAt);
        jdbc.update("""
                INSERT INTO match_groups(id, attempt_id, festival_id, status, confirmed_member_count,
                    confirmed_at, created_at, updated_at)
                VALUES (9824001, 9824000, ?, 'CONFIRMED', 2, ?, ?, ?)
                """, FESTIVAL, confirmedAt, confirmedAt, confirmedAt);
        jdbc.update("""
                INSERT INTO match_group_members(group_id, member_id, status, allow_minimum_two,
                    created_at, updated_at)
                VALUES (9824001, ?, 'JOINED', FALSE, ?, ?), (9824001, ?, 'JOINED', FALSE, ?, ?)
                """, USER, confirmedAt, confirmedAt, OTHER, confirmedAt, confirmedAt);
    }

    private String groupMemberStatus(long memberId) {
        return jdbc.queryForObject(
                "SELECT status FROM match_group_members WHERE member_id=?", String.class, memberId);
    }

    private void insertCheckinAndPool(String poolStatus) {
        jdbc.update("""
                INSERT INTO festival_checkins(id, member_id, festival_id, distance_meters, status,
                    checked_in_at, expires_at, created_at, updated_at)
                VALUES (9822001, ?, ?, 1, 'ACTIVE', ?, ?, ?, ?)
                """, USER, FESTIVAL, NOW.minusMinutes(1), NOW.plusHours(1), NOW, NOW);
        jdbc.update("""
                INSERT INTO match_pools(id, member_id, festival_id, checkin_id, preferred_group_size,
                    allow_minimum_two, tags, status, entered_at, search_expires_at, created_at, updated_at)
                VALUES (9823001, ?, ?, 9822001, 2, false, '[]', ?, ?, ?, ?, ?)
                """, USER, FESTIVAL, poolStatus, NOW, NOW.plusMinutes(1), NOW, NOW);
    }

    private void insertMember(long id, String nickname, String role, String status) {
        jdbc.update("""
                INSERT INTO members(id, provider, provider_user_id, nickname, role, status, created_at, updated_at)
                VALUES (?, 'KAKAO', ?, ?, ?, ?, ?, ?)
                """, id, "withdrawal-" + id, nickname, role, status, NOW.minusDays(10), NOW.minusDays(10));
    }

    private long count(String table, String column, long value) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + "=?", Long.class, value);
    }

    private String status(long memberId) {
        return jdbc.queryForObject("SELECT status FROM members WHERE id=?", String.class, memberId);
    }

    private String nickname(long memberId) {
        return jdbc.queryForObject("SELECT nickname FROM members WHERE id=?", String.class, memberId);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock clock() {
            return Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
