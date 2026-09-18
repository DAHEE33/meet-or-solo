package com.survey.meetorsolo.domain.festival.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.festival.history.dto.CheckinHistoryItemResponse;
import com.survey.meetorsolo.domain.festival.history.dto.CheckinHistoryResponse;
import com.survey.meetorsolo.domain.festival.history.service.CheckinHistoryService;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 체크인 기록 조회를 실제 PostgreSQL로 검증한다.
 *
 * <p>native query의 row-value cursor 비교와, 저장된 status가 아니라 유효기간으로 판정하는
 * 만료 표시가 핵심이다. 둘 다 DB 없이는 확인할 수 없다.
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
class CheckinHistoryIntegrationTest {

    private static final long ME = 9_310_001L;
    private static final long OTHER_MEMBER = 9_310_002L;
    private static final long FESTIVAL = 9_320_001L;
    private static final long OTHER_FESTIVAL = 9_320_002L;

    @Autowired CheckinHistoryService history;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 만료된_체크인과_취소된_체크인도_최신순으로_함께_남긴다() {
        insertFoundation();
        OffsetDateTime now = OffsetDateTime.now();
        insertCheckin(9_330_001L, ME, FESTIVAL, "ACTIVE", now.minusDays(2), now.minusDays(2).plusHours(1), 120);
        insertCheckin(9_330_002L, ME, FESTIVAL, "CANCELLED", now.minusDays(1), now.minusDays(1).plusHours(1), 30);
        // 유효한 ACTIVE는 다른 축제에 둔다. uq_festival_checkins_member_festival_active가
        // (member_id, festival_id)에 ACTIVE 행을 1건만 허용한다.
        insertCheckin(9_330_003L, ME, OTHER_FESTIVAL, "ACTIVE", now.minusMinutes(5), now.plusMinutes(55), 80);

        CheckinHistoryResponse response = history.getMyHistory(ME, null, null);

        assertThat(response.items()).extracting(CheckinHistoryItemResponse::checkinId)
                .containsExactly(9_330_003L, 9_330_002L, 9_330_001L);
        assertThat(response.pagination().hasNext()).isFalse();
        assertThat(response.pagination().nextCursor()).isNull();
    }

    @Test
    void 유효기간이_지난_ACTIVE_체크인은_EXPIRED로_내린다() {
        insertFoundation();
        OffsetDateTime now = OffsetDateTime.now();
        // DB에 EXPIRED를 기록하는 배치가 없어 지나간 체크인도 status는 ACTIVE로 남아 있다.
        insertCheckin(9_330_001L, ME, FESTIVAL, "ACTIVE", now.minusDays(2), now.minusDays(2).plusHours(1), 120);
        insertCheckin(9_330_002L, ME, FESTIVAL, "CANCELLED", now.minusHours(3), now.minusHours(2), 30);
        insertCheckin(9_330_003L, ME, OTHER_FESTIVAL, "ACTIVE", now.minusMinutes(5), now.plusMinutes(55), 80);

        CheckinHistoryResponse response = history.getMyHistory(ME, null, null);

        assertThat(item(response, 9_330_001L).status()).isEqualTo("EXPIRED");
        assertThat(item(response, 9_330_002L).status()).isEqualTo("CANCELLED");
        assertThat(item(response, 9_330_003L).status()).isEqualTo("ACTIVE");
    }

    @Test
    void 저장된_expires_at이_한_시간보다_길어도_한_시간_정책으로_만료시킨다() {
        insertFoundation();
        OffsetDateTime now = OffsetDateTime.now();
        // V17 이전에 만들어진 행처럼 expires_at이 멀리 있어도 유효기간은 1시간이다.
        insertCheckin(9_330_001L, ME, FESTIVAL, "ACTIVE", now.minusHours(3), now.plusHours(3), 40);

        assertThat(item(history.getMyHistory(ME, null, null), 9_330_001L).status()).isEqualTo("EXPIRED");
    }

    @Test
    void 다른_회원의_체크인은_보이지_않는다() {
        insertFoundation();
        OffsetDateTime now = OffsetDateTime.now();
        insertCheckin(9_330_001L, OTHER_MEMBER, FESTIVAL, "ACTIVE", now.minusMinutes(5), now.plusMinutes(55), 10);

        assertThat(history.getMyHistory(ME, null, null).items()).isEmpty();
    }

    @Test
    void cursor로_다음_페이지를_이어_읽고_중복이나_누락이_없다() {
        insertFoundation();
        OffsetDateTime now = OffsetDateTime.now();
        for (int index = 0; index < 5; index++) {
            insertCheckin(
                    9_330_001L + index, ME, FESTIVAL, "CANCELLED",
                    now.minusHours(index + 1L), now.minusHours(index + 1L).plusMinutes(30), 50);
        }

        CheckinHistoryResponse first = history.getMyHistory(ME, null, 2);
        assertThat(first.items()).extracting(CheckinHistoryItemResponse::checkinId)
                .containsExactly(9_330_001L, 9_330_002L);
        assertThat(first.pagination().hasNext()).isTrue();

        CheckinHistoryResponse second = history.getMyHistory(ME, first.pagination().nextCursor(), 2);
        assertThat(second.items()).extracting(CheckinHistoryItemResponse::checkinId)
                .containsExactly(9_330_003L, 9_330_004L);

        CheckinHistoryResponse third = history.getMyHistory(ME, second.pagination().nextCursor(), 2);
        assertThat(third.items()).extracting(CheckinHistoryItemResponse::checkinId)
                .containsExactly(9_330_005L);
        assertThat(third.pagination().hasNext()).isFalse();
        assertThat(third.pagination().nextCursor()).isNull();
    }

    @Test
    void 체크인_시각이_같아도_id로_순서가_고정된다() {
        insertFoundation();
        OffsetDateTime sameMoment = OffsetDateTime.now().minusHours(2);
        insertCheckin(9_330_001L, ME, FESTIVAL, "CANCELLED", sameMoment, sameMoment.plusHours(1), 10);
        insertCheckin(9_330_002L, ME, OTHER_FESTIVAL, "CANCELLED", sameMoment, sameMoment.plusHours(1), 20);

        CheckinHistoryResponse first = history.getMyHistory(ME, null, 1);
        CheckinHistoryResponse second = history.getMyHistory(ME, first.pagination().nextCursor(), 1);

        assertThat(first.items()).extracting(CheckinHistoryItemResponse::checkinId).containsExactly(9_330_002L);
        assertThat(second.items()).extracting(CheckinHistoryItemResponse::checkinId).containsExactly(9_330_001L);
    }

    @Test
    void 축제_이름과_거리를_함께_내리고_원본_좌표는_담지_않는다() {
        insertFoundation();
        OffsetDateTime now = OffsetDateTime.now();
        insertCheckin(9_330_001L, ME, FESTIVAL, "ACTIVE", now.minusMinutes(5), now.plusMinutes(55), 137);

        CheckinHistoryItemResponse item = history.getMyHistory(ME, null, null).items().get(0);

        assertThat(item.festivalId()).isEqualTo(FESTIVAL);
        assertThat(item.festivalTitle()).isEqualTo("체크인 기록 축제");
        assertThat(item.festivalAddress()).isEqualTo("강원 어딘가 1");
        assertThat(item.distanceMeters()).isEqualTo(137);
    }

    @Test
    void 잘못된_cursor는_400으로_거부한다() {
        insertFoundation();

        assertThatThrownBy(() -> history.getMyHistory(ME, "not-a-cursor", null))
                .isInstanceOf(BusinessException.class);
    }

    private void insertFoundation() {
        insertMember(ME, "checkin-history-me");
        insertMember(OTHER_MEMBER, "checkin-history-other");
        insertFestival(FESTIVAL, "checkin-history-festival", "체크인 기록 축제", "강원 어딘가 1");
        insertFestival(OTHER_FESTIVAL, "checkin-history-festival-2", "다른 축제", "강원 어딘가 2");
    }

    private void insertMember(long id, String providerUserId) {
        jdbc.update("""
                INSERT INTO members (id, provider, provider_user_id, nickname, status, created_at, updated_at)
                VALUES (?, 'KAKAO', ?, ?, 'ACTIVE', now(), now())
                """, id, providerUserId, providerUserId);
    }

    private void insertFestival(long id, String contentId, String title, String address) {
        jdbc.update("""
                INSERT INTO festivals (id, content_id, content_type_id, title, address, status, map_x, map_y,
                                       created_at, updated_at)
                VALUES (?, ?, '15', ?, ?, 'ACTIVE', 128.1, 37.1, now(), now())
                """, id, contentId, title, address);
    }

    private void insertCheckin(
            long id,
            long memberId,
            long festivalId,
            String status,
            OffsetDateTime checkedInAt,
            OffsetDateTime expiresAt,
            int distanceMeters
    ) {
        jdbc.update("""
                INSERT INTO festival_checkins (id, member_id, festival_id, distance_meters, status,
                                               checked_in_at, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
                """, id, memberId, festivalId, distanceMeters, status, checkedInAt, expiresAt);
    }

    private static CheckinHistoryItemResponse item(CheckinHistoryResponse response, long checkinId) {
        return response.items().stream()
                .filter(candidate -> candidate.checkinId() == checkinId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("체크인 기록에 " + checkinId + "이 없습니다."));
    }
}
