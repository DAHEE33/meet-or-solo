package com.survey.meetorsolo.domain.matching.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.service.FestivalCheckinService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 체크인 취소 → {@link FestivalCheckinCancelledEvent} 발행 → {@link FestivalCheckinCancelledEventHandler}
 * 구독까지 실제 트랜잭션 커밋을 거쳐 동작하는지 확인한다. 테스트 메서드가 자체 트랜잭션으로 감싸이면
 * {@code AFTER_COMMIT} 리스너가 아예 실행되지 않으므로, 이 클래스는 의도적으로 {@code @Transactional}을
 * 붙이지 않고 각 테스트가 직접 만든 row를 수동으로 정리한다.
 * ({@code docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md} 참고)
 */
@SpringBootTest(properties = {
        "app.matching.scheduler.enabled=false",
        "app.matching.no-show-scheduler.enabled=false"
})
@Testcontainers
class FestivalCheckinCancelledEventHandlerIntegrationTest {

    private static final long FESTIVAL_A_ID = 9_400_001L;
    private static final long FESTIVAL_B_ID = 9_400_002L;
    private static final long MEMBER_ID = 9_410_001L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-24T15:00:00+09:00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private FestivalCheckinService checkinService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanup();
        insertFestival(FESTIVAL_A_ID, new BigDecimal("128.1000000000"), new BigDecimal("37.1000000000"));
        insertFestival(FESTIVAL_B_ID, new BigDecimal("129.1000000000"), new BigDecimal("38.1000000000"));
        jdbc.update("""
                INSERT INTO members(
                    id, provider, provider_user_id, nickname, role, status, created_at, updated_at
                ) VALUES (?, 'KAKAO', ?, 'fixture', 'USER', 'ACTIVE', ?, ?)
                """, MEMBER_ID, "checkin-cancel-fixture-" + MEMBER_ID, NOW.minusDays(1), NOW.minusDays(1));
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 다른_축제로_재체크인하면_기존_축제의_WAITING_pool이_CANCELLED로_정리된다() {
        FestivalCheckinResponse checkinA = checkinAtFestivalA();
        long poolId = insertWaitingPool(checkinA.id());

        checkinAtFestivalB();

        assertThat(poolStatus(poolId)).isEqualTo("CANCELLED");
    }

    @Test
    void LOCKED_상태의_pool은_재체크인으로_건드리지_않는다() {
        FestivalCheckinResponse checkinA = checkinAtFestivalA();
        long poolId = insertWaitingPool(checkinA.id());
        jdbc.update("UPDATE match_pools SET status='LOCKED', locked_at=?, lock_token='fixture-token' WHERE id=?",
                NOW, poolId);

        checkinAtFestivalB();

        assertThat(poolStatus(poolId)).isEqualTo("LOCKED");
    }

    @Test
    void PROPOSED_상태의_pool도_재체크인으로_건드리지_않는다() {
        FestivalCheckinResponse checkinA = checkinAtFestivalA();
        long poolId = insertWaitingPool(checkinA.id());
        jdbc.update("UPDATE match_pools SET status='PROPOSED', locked_at=NULL, lock_token=NULL WHERE id=?", poolId);

        checkinAtFestivalB();

        assertThat(poolStatus(poolId)).isEqualTo("PROPOSED");
    }

    /**
     * {@code docs/21_CHECKIN_MATCH_POOL_INTEGRATION_DESIGN.md} 6장이 요구한 것은 "같은 축제로
     * 재체크인(이번 이벤트와 무관) 시 기존 동작이 깨지지 않는지 회귀 확인"이다. 여기서 말하는
     * 기존 동작은 같은 문서 1.1절 4번 단계의 내용, 즉 취소 UPDATE를 새 INSERT보다 먼저 flush해
     * {@code uq_festival_checkins_member_festival_active} 부분 unique index 위반을 막는 것이다.
     * 따라서 이 테스트가 지켜야 할 것은 pool 상태가 아니라 재체크인 성공 자체다.
     *
     * <p>pool 상태는 다음과 같이 확정했다. 취소되는 체크인마다 이벤트가 발행되므로 같은 축제로
     * 재체크인해도 그 축제의 {@code WAITING} pool은 {@code CANCELLED}가 된다. 이 경로는 화면으로는
     * 도달할 수 없다. {@code WAITING} pool의 검색 window가 60초인데
     * ({@code MatchPoolEntryService}) 그 60초 동안 체크인은 아직 55분 넘게 유효하고,
     * {@code FestivalDetailPage}는 이미 체크인한 축제의 체크인 버튼을 숨긴다
     * ({@code !isCheckedIntoThisFestival}). 체크인이 1시간 뒤 만료돼 재체크인할 때는 pool이 이미
     * {@code EXPIRED}라 취소 대상이 없다.
     */
    @Test
    void 같은_축제_재체크인은_unique_index_위반_없이_기존_체크인을_대체한다() {
        FestivalCheckinResponse first = checkinAtFestivalA();
        long poolId = insertWaitingPool(first.id());

        FestivalCheckinResponse second = checkinAtFestivalA();

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(checkinStatus(first.id())).isEqualTo("CANCELLED");
        assertThat(checkinStatus(second.id())).isEqualTo("ACTIVE");
        assertThat(activeCheckinCount(FESTIVAL_A_ID)).isEqualTo(1);
        assertThat(poolStatus(poolId)).isEqualTo("CANCELLED");
    }

    private FestivalCheckinResponse checkinAtFestivalA() {
        return checkinService.checkIn(MEMBER_ID, FESTIVAL_A_ID,
                new CheckInRequest(new BigDecimal("37.1000000000"), new BigDecimal("128.1000000000"), null));
    }

    private FestivalCheckinResponse checkinAtFestivalB() {
        return checkinService.checkIn(MEMBER_ID, FESTIVAL_B_ID,
                new CheckInRequest(new BigDecimal("38.1000000000"), new BigDecimal("129.1000000000"), null));
    }

    private String checkinStatus(long checkinId) {
        return jdbc.queryForObject("SELECT status FROM festival_checkins WHERE id=?", String.class, checkinId);
    }

    private int activeCheckinCount(long festivalId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM festival_checkins WHERE member_id=? AND festival_id=? AND status='ACTIVE'",
                Integer.class, MEMBER_ID, festivalId);
    }

    private String poolStatus(long poolId) {
        return jdbc.queryForObject("SELECT status FROM match_pools WHERE id=?", String.class, poolId);
    }

    private long insertWaitingPool(long checkinId) {
        long poolId = 9_420_000L + checkinId;
        jdbc.update("""
                INSERT INTO match_pools(
                    id, member_id, festival_id, checkin_id, preferred_group_size, allow_minimum_two,
                    tags, status, entered_at, search_expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 2, false, '[]', 'WAITING', ?, ?, ?, ?)
                """, poolId, MEMBER_ID, FESTIVAL_A_ID, checkinId, NOW, NOW.plusSeconds(60), NOW, NOW);
        return poolId;
    }

    private void insertFestival(long id, BigDecimal mapX, BigDecimal mapY) {
        jdbc.update("""
                INSERT INTO festivals(
                    id, content_id, content_type_id, title, status, map_x, map_y, created_at, updated_at
                ) VALUES (?, ?, '15', 'fixture', 'ACTIVE', ?, ?, ?, ?)
                """, id, "checkin-cancel-fixture-" + id, mapX, mapY, NOW.minusDays(1), NOW.minusDays(1));
    }

    private void cleanup() {
        jdbc.update("DELETE FROM match_pools WHERE member_id = ?", MEMBER_ID);
        jdbc.update("DELETE FROM festival_checkins WHERE member_id = ?", MEMBER_ID);
        jdbc.update("DELETE FROM members WHERE id = ?", MEMBER_ID);
        jdbc.update("DELETE FROM festivals WHERE id IN (?, ?)", FESTIVAL_A_ID, FESTIVAL_B_ID);
    }
}
