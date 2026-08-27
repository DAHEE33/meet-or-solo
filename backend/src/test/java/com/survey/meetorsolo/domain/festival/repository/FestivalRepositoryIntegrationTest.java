package com.survey.meetorsolo.domain.festival.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Sql(statements = {
        // 만남 장소가 이미 있는 ACTIVE 축제 — 백필 대상에서 제외돼야 한다.
        "INSERT INTO festivals (id, content_id, content_type_id, title, status, map_x, map_y, created_at, updated_at) "
                + "VALUES (9200001, 'repo-fixture-has-point', '15', '장소 있는 축제', 'ACTIVE', 128.1, 37.1, now(), now())",
        "INSERT INTO festival_meeting_points (id, festival_id, kakao_place_id, name, address, map_x, map_y, status, assignment_order, created_at, updated_at) "
                + "VALUES (9200101, 9200001, 'repo-fixture-place', '기존 장소', '강원 어딘가', 128.1001, 37.1001, 'INACTIVE', 0, now(), now())",
        // 만남 장소가 0건인 ACTIVE 축제 — 백필 대상이어야 한다.
        "INSERT INTO festivals (id, content_id, content_type_id, title, status, map_x, map_y, created_at, updated_at) "
                + "VALUES (9200002, 'repo-fixture-no-point', '15', '장소 없는 축제', 'ACTIVE', 128.2, 37.2, now(), now())",
        // 만남 장소가 0건이지만 ENDED인 축제 — ACTIVE가 아니므로 대상에서 제외돼야 한다.
        "INSERT INTO festivals (id, content_id, content_type_id, title, status, map_x, map_y, created_at, updated_at) "
                + "VALUES (9200003, 'repo-fixture-ended', '15', '종료된 축제', 'ENDED', 128.3, 37.3, now(), now())"
})
class FestivalRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    FestivalRepository festivals;

    @Test
    void 장소가_0건인_ACTIVE_축제만_반환한다() {
        var result = festivals.findAllByStatusWithoutMeetingPoint(FestivalStatus.ACTIVE);

        assertThat(result)
                .extracting(festival -> festival.getContentId())
                .containsExactly("repo-fixture-no-point");
    }

    @Test
    void findVisibleFestivals는_ACTIVE만_요약_정보_프로젝션으로_반환한다() {
        var page = festivals.findVisibleFestivals(
                FestivalStatus.ACTIVE, LocalDate.now(), "", PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(summary -> summary.contentId())
                .containsExactlyInAnyOrder("repo-fixture-has-point", "repo-fixture-no-point");
        assertThat(page.getContent())
                .filteredOn(summary -> summary.contentId().equals("repo-fixture-has-point"))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.title()).isEqualTo("장소 있는 축제");
                    assertThat(summary.status()).isEqualTo(FestivalStatus.ACTIVE);
                });
    }

    @Test
    void findAllVisibleWithinBoundingBox는_범위_밖_좌표를_제외한다() {
        var result = festivals.findAllVisibleWithinBoundingBox(
                FestivalStatus.ACTIVE, LocalDate.now(),
                new BigDecimal("128.05"), new BigDecimal("128.15"),
                new BigDecimal("37.05"), new BigDecimal("37.15"));

        assertThat(result)
                .extracting(festival -> festival.getContentId())
                .containsExactly("repo-fixture-has-point");
    }
}
