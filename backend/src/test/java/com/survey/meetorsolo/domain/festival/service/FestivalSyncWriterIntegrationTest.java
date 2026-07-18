package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void PostgreSQL에_JSONB를_포함한_축제를_insert한_뒤_contentId로_update한다() {
        String contentId = "test-" + UUID.randomUUID();
        LocalDate syncDate = LocalDate.of(2026, 7, 18);

        FestivalSyncWriteResult insertResult = writer.upsert(
                List.of(syncData(contentId, "최초 제목")),
                syncDate
        );
        FestivalSyncWriteResult updateResult = writer.upsert(
                List.of(syncData(contentId, "변경 제목")),
                syncDate
        );

        var saved = festivalRepository.findByContentId(contentId).orElseThrow();
        assertThat(insertResult.insertedCount()).isEqualTo(1);
        assertThat(updateResult.updatedCount()).isEqualTo(1);
        assertThat(saved.getTitle()).isEqualTo("변경 제목");
        assertThat(saved.getRawData()).containsEntry("contentid", contentId);
        assertThat(festivalRepository.findAllByContentIdIn(List.of(contentId))).hasSize(1);
    }

    private FestivalSyncData syncData(String contentId, String title) {
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
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", contentId, "title", title)
        );
    }
}
