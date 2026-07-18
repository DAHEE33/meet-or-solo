package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class FestivalSyncMapperTest {

    private final FestivalSyncMapper mapper = new FestivalSyncMapper(new ObjectMapper());

    @Test
    void 관광공사_축제_DTO를_DB_동기화_데이터로_변환한다() {
        OffsetDateTime syncedAt = OffsetDateTime.parse("2026-07-18T10:00:00+09:00");

        var result = mapper.toSyncData(festivalItem("100", "테스트 축제"), syncedAt);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().contentId()).isEqualTo("100");
        assertThat(result.orElseThrow().address()).isEqualTo("강원특별자치도 테스트시 테스트 장소");
        assertThat(result.orElseThrow().eventStartDate()).isEqualTo("2026-07-20");
        assertThat(result.orElseThrow().eventEndDate()).isEqualTo("2026-07-22");
        assertThat(result.orElseThrow().mapX()).isEqualByComparingTo(new BigDecimal("128.1234567890"));
        assertThat(result.orElseThrow().mapY()).isEqualByComparingTo(new BigDecimal("37.1234567890"));
        assertThat(result.orElseThrow().originImageUrl())
                .isEqualTo("https://example.com/image.jpg");
        assertThat(result.orElseThrow().thumbnailUrl())
                .isEqualTo("https://example.com/thumbnail.jpg");
        assertThat(result.orElseThrow().rawData()).containsEntry("contentid", "100");
        assertThat(result.orElseThrow().syncedAt()).isEqualTo(syncedAt);
    }

    @Test
    void HTTP가_아닌_이미지_URL은_저장_대상에서_제외한다() {
        SearchFestivalItem item = new SearchFestivalItem(
                "강원특별자치도 테스트시",
                null,
                null,
                "100",
                "15",
                null,
                "20260720",
                "20260722",
                "javascript:alert(1)",
                null,
                null,
                "128.1",
                "37.1",
                null,
                null,
                null,
                "테스트 축제",
                "51",
                "110",
                "EV",
                "EV01",
                null,
                null,
                null
        );

        var result = mapper.toSyncData(
                item,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00")
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().originImageUrl()).isNull();
        assertThat(result.orElseThrow().thumbnailUrl()).isNull();
    }

    @Test
    void 필수값이나_날짜가_잘못된_항목은_동기화에서_제외한다() {
        OffsetDateTime syncedAt = OffsetDateTime.parse("2026-07-18T10:00:00+09:00");
        SearchFestivalItem missingContentId = festivalItem(null, "테스트 축제");
        SearchFestivalItem invalidDate = new SearchFestivalItem(
                "강원특별자치도 테스트시",
                null,
                null,
                "100",
                "15",
                null,
                "20261340",
                "20260722",
                null,
                null,
                null,
                "128.1",
                "37.1",
                null,
                null,
                null,
                "테스트 축제",
                "51",
                null,
                "EV",
                "EV01",
                null,
                null,
                null
        );

        assertThat(mapper.toSyncData(missingContentId, syncedAt)).isEmpty();
        assertThat(mapper.toSyncData(invalidDate, syncedAt)).isEmpty();
    }

    private SearchFestivalItem festivalItem(String contentId, String title) {
        return new SearchFestivalItem(
                "강원특별자치도 테스트시",
                "테스트 장소",
                "00000",
                contentId,
                "15",
                "20260701120000",
                "20260720",
                "20260722",
                "https://example.com/image.jpg",
                "https://example.com/thumbnail.jpg",
                "Type3",
                "128.123456789",
                "37.123456789",
                "6",
                "20260718120000",
                "033-000-0000",
                title,
                "51",
                "110",
                "EV",
                "EV01",
                "EV010100",
                "진행예정",
                "문화관광축제"
        );
    }
}
