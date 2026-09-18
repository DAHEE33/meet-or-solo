package com.survey.meetorsolo.domain.tourplace.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TourPlaceSyncMapperTest {

    private final TourPlaceSyncMapper mapper = new TourPlaceSyncMapper(new ObjectMapper());

    @Test
    void 관광공사_관광지_DTO를_DB_동기화_데이터로_변환한다() {
        OffsetDateTime syncedAt = OffsetDateTime.parse("2026-07-18T10:00:00+09:00");

        var result = mapper.toSyncData(tourPlaceItem("100", "테스트 관광지"), "12", syncedAt);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().contentId()).isEqualTo("100");
        assertThat(result.orElseThrow().address()).isEqualTo("강원특별자치도 테스트시 테스트 장소");
        assertThat(result.orElseThrow().mapX()).isEqualByComparingTo(new BigDecimal("128.1234567890"));
        assertThat(result.orElseThrow().mapY()).isEqualByComparingTo(new BigDecimal("37.1234567890"));
        assertThat(result.orElseThrow().tel()).isEqualTo("033-000-0000");
        assertThat(result.orElseThrow().imageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(result.orElseThrow().rawData()).containsEntry("contentid", "100");
        assertThat(result.orElseThrow().syncedAt()).isEqualTo(syncedAt);
    }

    @Test
    void 요청한_콘텐츠_타입과_다르면_동기화에서_제외한다() {
        OffsetDateTime syncedAt = OffsetDateTime.parse("2026-07-18T10:00:00+09:00");

        var result = mapper.toSyncData(tourPlaceItem("100", "테스트 관광지"), "39", syncedAt);

        assertThat(result).isEmpty();
    }

    @Test
    void HTTP가_아닌_이미지_URL은_저장_대상에서_제외한다() {
        SearchTourPlaceItem item = new SearchTourPlaceItem(
                "강원특별자치도 테스트시",
                null,
                null,
                "100",
                "12",
                null,
                "javascript:alert(1)",
                null,
                "Type3",
                "128.1",
                "37.1",
                "6",
                null,
                "033-000-0000",
                "테스트 관광지",
                "A01",
                "A0101",
                "A01010100",
                "51",
                "110"
        );

        var result = mapper.toSyncData(
                item,
                "12",
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00")
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().imageUrl()).isNull();
    }

    @Test
    void 필수값이_없는_항목은_동기화에서_제외한다() {
        OffsetDateTime syncedAt = OffsetDateTime.parse("2026-07-18T10:00:00+09:00");
        SearchTourPlaceItem missingContentId = tourPlaceItem(null, "테스트 관광지");
        SearchTourPlaceItem missingTitle = tourPlaceItem("100", null);

        assertThat(mapper.toSyncData(missingContentId, "12", syncedAt)).isEmpty();
        assertThat(mapper.toSyncData(missingTitle, "12", syncedAt)).isEmpty();
    }

    private SearchTourPlaceItem tourPlaceItem(String contentId, String title) {
        return new SearchTourPlaceItem(
                "강원특별자치도 테스트시",
                "테스트 장소",
                "00000",
                contentId,
                "12",
                "20260701120000",
                "https://example.com/image.jpg",
                "https://example.com/thumbnail.jpg",
                "Type3",
                "128.123456789",
                "37.123456789",
                "6",
                "20260718120000",
                "033-000-0000",
                title,
                "A01",
                "A0101",
                "A01010100",
                "51",
                "110"
        );
    }
}
