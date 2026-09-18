package com.survey.meetorsolo.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.notification.entity.Notification;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 알림함 조회·정리 query를 실제 PostgreSQL에서 확인한다({@code docs/32} 3.3).
 *
 * <p>단위 테스트로 덮이지 않는 부분이 여기다 — 보관 건수 정리는 JPQL에 LIMIT이 없어 native
 * query로 썼고, 중복 방지는 DB unique index가 최종 방어선이다. 둘 다 mock으로는 검증되지 않는다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Sql("/fixtures/matching-engine-foundation.sql")
class NotificationRepositoryIntegrationTest {

    private static final long MEMBER_ID = 9_110_001L;
    private static final long OTHER_MEMBER_ID = 9_110_002L;
    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-07-29T12:00:00+09:00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    NotificationRepository notifications;

    private Notification save(long memberId, String reason, int minutesAgo) {
        return notifications.saveAndFlush(Notification.of(
                memberId, reason, null, BASE.minusMinutes(minutesAgo), BASE.minusMinutes(minutesAgo)));
    }

    @Test
    void 내_알림만_최신순으로_돌려준다() {
        save(MEMBER_ID, "MATCH_PROPOSED", 30);
        save(MEMBER_ID, "MATCH_CONFIRMED", 10);
        save(OTHER_MEMBER_ID, "MATCH_COMPLETED", 5);

        List<Notification> found =
                notifications.findByMemberIdOrderByCreatedAtDescIdDesc(MEMBER_ID, Limit.of(20));

        assertThat(found).extracting(Notification::getReason)
                .containsExactly("MATCH_CONFIRMED", "MATCH_PROPOSED");
    }

    @Test
    void 같은_사유와_발생시각은_한_번만_저장된다() {
        save(MEMBER_ID, "MATCH_PROPOSED", 30);

        assertThatThrownBy(() -> save(MEMBER_ID, "MATCH_PROPOSED", 30))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 읽음_처리는_내_읽지_않은_알림만_바꾼다() {
        save(MEMBER_ID, "MATCH_PROPOSED", 30);
        save(OTHER_MEMBER_ID, "MATCH_PROPOSED", 30);

        int changed = notifications.markAllRead(MEMBER_ID, BASE);

        assertThat(changed).isEqualTo(1);
        assertThat(notifications.countByMemberIdAndReadAtIsNull(MEMBER_ID)).isZero();
        assertThat(notifications.countByMemberIdAndReadAtIsNull(OTHER_MEMBER_ID)).isEqualTo(1);
    }

    @Test
    void 보관_기간이_지난_알림을_지운다() {
        save(MEMBER_ID, "MATCH_PROPOSED", 60 * 24 * 40);
        save(MEMBER_ID, "MATCH_CONFIRMED", 10);

        int deleted = notifications.deleteOlderThan(MEMBER_ID, BASE.minusDays(30));

        assertThat(deleted).isEqualTo(1);
        assertThat(notifications.findByMemberIdOrderByCreatedAtDescIdDesc(MEMBER_ID, Limit.of(20)))
                .extracting(Notification::getReason)
                .containsExactly("MATCH_CONFIRMED");
    }

    /** JPQL에 LIMIT이 없어 native query로 쓴 부분이다. */
    @Test
    void 보관_건수를_넘으면_오래된_것부터_지운다() {
        for (int index = 0; index < 5; index++) {
            save(MEMBER_ID, "MATCH_PROPOSED_" + index, index);
        }
        save(OTHER_MEMBER_ID, "MATCH_PROPOSED_0", 0);

        int deleted = notifications.deleteBeyondNewest(MEMBER_ID, 2);

        assertThat(deleted).isEqualTo(3);
        assertThat(notifications.findByMemberIdOrderByCreatedAtDescIdDesc(MEMBER_ID, Limit.of(20)))
                .extracting(Notification::getReason)
                .containsExactly("MATCH_PROPOSED_0", "MATCH_PROPOSED_1");
        // 남의 알림은 건드리지 않는다.
        assertThat(notifications.findByMemberIdOrderByCreatedAtDescIdDesc(OTHER_MEMBER_ID, Limit.of(20)))
                .hasSize(1);
    }
}
