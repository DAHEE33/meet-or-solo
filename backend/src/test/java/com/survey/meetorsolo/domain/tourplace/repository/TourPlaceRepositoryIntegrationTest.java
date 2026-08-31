package com.survey.meetorsolo.domain.tourplace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Sql(statements = {
        // 목록/검색·반경 조회 대상인 ACTIVE 관광지.
        "INSERT INTO tour_places (id, content_id, content_type_id, title, address, map_x, map_y, status, created_at, updated_at) "
                + "VALUES (9300001, 'repo-place-near', '12', '가까운 관광지', '강원 테스트로 1', 128.1, 37.1, 'ACTIVE', now(), now())",
        // bounding box 밖(멀리 떨어진) ACTIVE 관광지 — 반경 조회에서 제외돼야 한다.
        "INSERT INTO tour_places (id, content_id, content_type_id, title, address, map_x, map_y, status, created_at, updated_at) "
                + "VALUES (9300002, 'repo-place-far', '12', '먼 관광지', '강원 다른로 1', 129.0, 38.0, 'ACTIVE', now(), now())",
        // HIDDEN 관광지 — 목록/반경 조회 모두 제외돼야 한다.
        "INSERT INTO tour_places (id, content_id, content_type_id, title, address, map_x, map_y, status, created_at, updated_at) "
                + "VALUES (9300003, 'repo-place-hidden', '12', '숨김 관광지', '강원 숨김로 1', 128.1, 37.1, 'HIDDEN', now(), now())"
})
class TourPlaceRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    TourPlaceRepository places;

    @Test
    void findVisiblePlaces는_ACTIVE만_목록_응답_프로젝션으로_반환한다() {
        var page = places.findVisiblePlaces(TourPlaceStatus.ACTIVE, null, null, "", PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(item -> item.contentId())
                .containsExactlyInAnyOrder("repo-place-near", "repo-place-far");
        assertThat(page.getContent())
                .filteredOn(item -> item.contentId().equals("repo-place-near"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.title()).isEqualTo("가까운 관광지");
                    assertThat(item.status()).isEqualTo(TourPlaceStatus.ACTIVE);
                });
    }

    @Test
    void findAllVisibleWithinBoundingBox는_범위_밖_좌표를_제외한다() {
        var result = places.findAllVisibleWithinBoundingBox(
                TourPlaceStatus.ACTIVE,
                new BigDecimal("128.05"), new BigDecimal("128.15"),
                new BigDecimal("37.05"), new BigDecimal("37.15"));

        assertThat(result)
                .extracting(place -> place.getContentId())
                .containsExactly("repo-place-near");
    }
}
