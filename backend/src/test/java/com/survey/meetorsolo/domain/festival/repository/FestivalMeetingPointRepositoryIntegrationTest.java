package com.survey.meetorsolo.domain.festival.repository;

import static org.assertj.core.api.Assertions.*;
import com.survey.meetorsolo.domain.festival.entity.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Sql("/fixtures/matching-engine-foundation.sql")
class FestivalMeetingPointRepositoryIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired FestivalMeetingPointRepository points;

    @Test
    void ACTIVE만_assignment_order와_id_순서로_조회한다() {
        FestivalMeetingPoint inactive = FestivalMeetingPoint.inactive(9_100_001L, "inactive-place",
                "비활성 장소", "강원 비활성로 1", new BigDecimal("128.3"),
                new BigDecimal("37.3"), 0);
        points.saveAndFlush(inactive);
        assertThat(points.findAllByFestivalIdAndStatusOrderByAssignmentOrderAscIdAsc(
                9_100_001L, FestivalMeetingPointStatus.ACTIVE))
                .extracting(FestivalMeetingPoint::getKakaoPlaceId)
                .containsExactly("fixture-place-a", "fixture-place-b");
    }

    @Test
    void 잘못된_좌표와_assignment_order는_DB_constraint가_거부한다() {
        FestivalMeetingPoint invalid = FestivalMeetingPoint.inactive(9_100_001L, "invalid-place",
                "잘못된 장소", "강원 잘못로 1", new BigDecimal("181"),
                new BigDecimal("37.3"), -1);
        assertThatThrownBy(() -> points.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_축제의_Kakao_장소_ID_중복은_거부한다() {
        FestivalMeetingPoint duplicate = FestivalMeetingPoint.inactive(9_100_001L, "fixture-place-a",
                "중복 장소", "강원 중복로 1", new BigDecimal("128.4"),
                new BigDecimal("37.4"), 30);
        assertThatThrownBy(() -> points.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
