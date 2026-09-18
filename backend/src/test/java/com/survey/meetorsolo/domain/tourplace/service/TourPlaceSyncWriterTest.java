package com.survey.meetorsolo.domain.tourplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourPlaceSyncWriterTest {

    @Mock
    private TourPlaceRepository tourPlaceRepository;

    @Test
    void contentId를_기준으로_신규_관광지와_기존_관광지를_upsert한다() {
        TourPlace existing = TourPlace.create(syncData("100", "이전 이름"));
        when(tourPlaceRepository.findAllByContentIdIn(List.of("100", "200")))
                .thenReturn(List.of(existing));
        TourPlaceSyncWriter writer = new TourPlaceSyncWriter(tourPlaceRepository);

        TourPlaceSyncWriteResult result = writer.upsertBatch(
                List.of(syncData("100", "변경 이름"), syncData("200", "신규 관광지"))
        );

        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.inactiveCount()).isZero();
        assertThat(existing.getTitle()).isEqualTo("변경 이름");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TourPlace>> placeCaptor = ArgumentCaptor.forClass(List.class);
        verify(tourPlaceRepository).saveAll(placeCaptor.capture());
        verify(tourPlaceRepository).flush();
        assertThat(placeCaptor.getValue())
                .extracting(TourPlace::getContentId)
                .containsExactly("100", "200");
    }

    @Test
    void 빈_배치는_저장을_시도하지_않는다() {
        TourPlaceSyncWriter writer = new TourPlaceSyncWriter(tourPlaceRepository);

        TourPlaceSyncWriteResult result = writer.upsertBatch(List.of());

        assertThat(result.insertedCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        verify(tourPlaceRepository, never()).findAllByContentIdIn(anyList());
        verify(tourPlaceRepository, never()).saveAll(anyList());
        verify(tourPlaceRepository, never()).flush();
    }

    @Test
    void 관측된_콘텐츠_ID가_없으면_해당_콘텐츠_타입의_ACTIVE_전체를_비활성_처리한다() {
        when(tourPlaceRepository.markAllActiveInScopeInactive(
                eq("12"),
                eq(TourPlaceStatus.ACTIVE),
                eq(TourPlaceStatus.INACTIVE),
                any(OffsetDateTime.class)
        )).thenReturn(3);
        TourPlaceSyncWriter writer = new TourPlaceSyncWriter(tourPlaceRepository);

        int inactiveCount = writer.markMissingInactive(scope());

        assertThat(inactiveCount).isEqualTo(3);
    }

    @Test
    void 관측된_콘텐츠_ID가_있으면_그중_없는_기존_ACTIVE만_비활성_처리한다() {
        when(tourPlaceRepository.markActiveMissingInScopeInactive(
                eq(Set.of("100", "200")),
                eq("12"),
                eq(TourPlaceStatus.ACTIVE),
                eq(TourPlaceStatus.INACTIVE),
                any(OffsetDateTime.class)
        )).thenReturn(1);
        TourPlaceSyncWriter writer = new TourPlaceSyncWriter(tourPlaceRepository);

        int inactiveCount = writer.markMissingInactive(scope("100", "200"));

        assertThat(inactiveCount).isEqualTo(1);
    }

    private TourPlaceSyncData syncData(String contentId, String title) {
        return new TourPlaceSyncData(
                contentId,
                "12",
                title,
                "강원특별자치도 테스트시",
                "51",
                "110",
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", contentId)
        );
    }

    private TourPlaceSyncScope scope(String... observedContentIds) {
        return new TourPlaceSyncScope(
                LocalDate.of(2026, 7, 18),
                "12",
                Set.of(observedContentIds)
        );
    }
}
