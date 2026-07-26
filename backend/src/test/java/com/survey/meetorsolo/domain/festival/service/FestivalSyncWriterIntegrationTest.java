package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.festival.sync.enabled=false"
})
@Transactional
class FestivalSyncWriterIntegrationTest {

    @Autowired
    private FestivalSyncWriter writer;

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private FestivalImageRepository festivalImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void PostgreSQL에_JSONB를_포함한_축제를_insert한_뒤_contentId로_update한다() {
        String contentId = "test-" + UUID.randomUUID();
        LocalDate syncDate = LocalDate.of(2026, 7, 18);

        FestivalSyncWriteResult insertResult = writer.upsert(
                List.of(syncData(contentId, "최초 제목")),
                scope(syncDate, contentId)
        );
        FestivalSyncWriteResult updateResult = writer.upsert(
                List.of(syncData(
                        contentId,
                        "변경 제목",
                        "https://example.com/updated-origin.jpg",
                        "https://example.com/updated-thumbnail.jpg"
                )),
                scope(syncDate, contentId)
        );
        writer.upsert(
                List.of(syncData(contentId, "이미지 누락 응답", null, null)),
                scope(syncDate, contentId)
        );

        var saved = festivalRepository.findByContentId(contentId).orElseThrow();
        assertThat(insertResult.insertedCount()).isEqualTo(1);
        assertThat(updateResult.updatedCount()).isEqualTo(1);
        assertThat(saved.getTitle()).isEqualTo("이미지 누락 응답");
        assertThat(saved.getRawData()).containsEntry("contentid", contentId);
        assertThat(festivalRepository.findAllByContentIdIn(List.of(contentId))).hasSize(1);
        assertThat(festivalImageRepository.findAllByFestivalIdIn(List.of(saved.getId())))
                .singleElement()
                .satisfies(image -> {
                    assertThat(image.getOriginImageUrl()).isEqualTo("https://example.com/updated-origin.jpg");
                    assertThat(image.getThumbnailUrl()).isEqualTo("https://example.com/updated-thumbnail.jpg");
                });
    }

    @Test
    void API_결과가_비어도_종료일이_지난_ACTIVE_축제를_ENDED로_정리한다() {
        String contentId = "test-ended-" + UUID.randomUUID();
        writer.upsert(
                List.of(syncData(contentId, "종료 예정 축제")),
                scope(LocalDate.of(2026, 7, 18), contentId)
        );

        FestivalSyncWriteResult result = writer.upsert(
                List.of(),
                scope(LocalDate.of(2026, 7, 23))
        );

        var saved = festivalRepository.findByContentId(contentId).orElseThrow();
        assertThat(result.endedCount()).isGreaterThanOrEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(FestivalStatus.ENDED);
    }

    @Test
    void 목록_조회는_ACTIVE이면서_조회일에_종료되지_않은_축제만_반환한다() {
        String activeContentId = "test-active-" + UUID.randomUUID();
        String endedContentId = "test-past-" + UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 7, 18);
        writer.upsert(
                List.of(
                        syncData(activeContentId, "노출 축제"),
                        syncDataWithPeriod(
                                endedContentId,
                                "종료 축제",
                                LocalDate.of(2026, 7, 1),
                                LocalDate.of(2026, 7, 10)
                        )
                ),
                scope(today, activeContentId, endedContentId)
        );

        var page = festivalRepository.findVisibleFestivals(
                FestivalStatus.ACTIVE,
                today,
                "",
                PageRequest.of(0, 10_000)
        );

        assertThat(page.getContent())
                .extracting(festival -> festival.getContentId())
                .contains(activeContentId)
                .doesNotContain(endedContentId);
    }

    @Test
    void 성공한_전체_동기화에서_누락된_ACTIVE만_INACTIVE로_변경한다() {
        LocalDate syncDate = LocalDate.of(2026, 7, 18);
        String presentContentId = "test-present-" + UUID.randomUUID();
        String missingContentId = "test-missing-" + UUID.randomUUID();
        String hiddenContentId = "test-hidden-" + UUID.randomUUID();
        writer.upsert(
                List.of(
                        syncData(presentContentId, "계속 노출되는 축제"),
                        syncData(missingContentId, "응답에서 사라진 축제"),
                        syncData(hiddenContentId, "운영자가 숨긴 축제")
                ),
                scope(syncDate, presentContentId, missingContentId, hiddenContentId)
        );
        jdbcTemplate.update(
                "update festivals set status = 'HIDDEN' where content_id = ?",
                hiddenContentId
        );

        FestivalSyncWriteResult result = writer.upsert(
                List.of(syncData(presentContentId, "계속 노출되는 축제")),
                scope(syncDate, presentContentId)
        );

        assertThat(result.inactiveCount()).isGreaterThanOrEqualTo(1);
        assertThat(festivalRepository.findByContentId(presentContentId).orElseThrow().getStatus())
                .isEqualTo(FestivalStatus.ACTIVE);
        assertThat(festivalRepository.findByContentId(missingContentId).orElseThrow().getStatus())
                .isEqualTo(FestivalStatus.INACTIVE);
        assertThat(festivalRepository.findByContentId(hiddenContentId).orElseThrow().getStatus())
                .isEqualTo(FestivalStatus.HIDDEN);
    }

    private FestivalSyncData syncData(String contentId, String title) {
        return syncData(
                contentId,
                title,
                "https://example.com/origin.jpg",
                "https://example.com/thumbnail.jpg"
        );
    }

    private FestivalSyncScope scope(LocalDate syncDate, String... observedContentIds) {
        return new FestivalSyncScope(
                syncDate,
                syncDate.minusDays(30),
                syncDate.plusDays(365),
                "51",
                Set.of(observedContentIds)
        );
    }

    private FestivalSyncData syncData(
            String contentId,
            String title,
            String originImageUrl,
            String thumbnailUrl
    ) {
        return new FestivalSyncData(
                contentId,
                "15",
                title,
                "강원특별자치도 테스트시",
                "51",
                "110",
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 22),
                null,
                null,
                originImageUrl,
                thumbnailUrl,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", contentId, "title", title)
        );
    }

    private FestivalSyncData syncDataWithPeriod(
            String contentId,
            String title,
            LocalDate eventStartDate,
            LocalDate eventEndDate
    ) {
        FestivalSyncData data = syncData(contentId, title);
        return new FestivalSyncData(
                data.contentId(),
                data.contentTypeId(),
                data.title(),
                data.address(),
                data.regionCode(),
                data.sigunguCode(),
                eventStartDate,
                eventEndDate,
                data.mapX(),
                data.mapY(),
                data.originImageUrl(),
                data.thumbnailUrl(),
                data.syncedAt(),
                data.rawData()
        );
    }
}
