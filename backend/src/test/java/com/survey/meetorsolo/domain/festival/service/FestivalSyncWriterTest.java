package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FestivalSyncWriterTest {

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private FestivalImageRepository festivalImageRepository;

    @Test
    void contentId를_기준으로_신규_축제와_기존_축제를_upsert한다() {
        LocalDate syncDate = LocalDate.of(2026, 7, 18);
        Festival existing = Festival.create(syncData("100", "이전 제목"), syncDate);
        when(festivalRepository.count()).thenReturn(1L);
        when(festivalRepository.findAllByContentIdIn(List.of("100", "200")))
                .thenReturn(List.of(existing));
        when(festivalRepository.markEndedBefore(
                eq(syncDate),
                eq(FestivalStatus.ENDED),
                eq(FestivalStatus.HIDDEN),
                any(OffsetDateTime.class)
        )).thenReturn(0);
        FestivalSyncWriter writer = new FestivalSyncWriter(
                festivalRepository,
                festivalImageRepository
        );

        FestivalSyncWriteResult result = writer.upsert(
                List.of(syncData("100", "변경 제목"), syncData("200", "신규 축제")),
                syncDate
        );

        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.synchronizedImageCount()).isZero();
        assertThat(result.endedCount()).isZero();
        assertThat(result.initialLoad()).isFalse();
        assertThat(existing.getTitle()).isEqualTo("변경 제목");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Festival>> festivalCaptor = ArgumentCaptor.forClass(List.class);
        verify(festivalRepository).saveAll(festivalCaptor.capture());
        verify(festivalRepository).flush();
        assertThat(festivalCaptor.getValue())
                .extracting(Festival::getContentId)
                .containsExactly("100", "200");
    }

    @Test
    void 동기화_결과가_비어_있어도_종료일이_지난_축제는_ENDED로_정리한다() {
        when(festivalRepository.count()).thenReturn(0L);
        when(festivalRepository.markEndedBefore(
                eq(LocalDate.of(2026, 7, 18)),
                eq(FestivalStatus.ENDED),
                eq(FestivalStatus.HIDDEN),
                any(OffsetDateTime.class)
        )).thenReturn(2);
        FestivalSyncWriter writer = new FestivalSyncWriter(
                festivalRepository,
                festivalImageRepository
        );

        FestivalSyncWriteResult result = writer.upsert(List.of(), LocalDate.of(2026, 7, 18));

        assertThat(result.initialLoad()).isTrue();
        assertThat(result.insertedCount()).isZero();
        assertThat(result.endedCount()).isEqualTo(2);
        verify(festivalRepository, never()).findAllByContentIdIn(anyList());
        verify(festivalRepository, never()).saveAll(anyList());
        verify(festivalRepository, never()).flush();
        verify(festivalImageRepository, never()).saveAll(anyList());
    }

    @Test
    void 관광공사_대표_이미지를_축제별_한_건으로_저장한다() {
        LocalDate syncDate = LocalDate.of(2026, 7, 18);
        Festival existing = Festival.create(syncData("100", "기존 축제"), syncDate);
        ReflectionTestUtils.setField(existing, "id", 1L);
        when(festivalRepository.count()).thenReturn(1L);
        when(festivalRepository.findAllByContentIdIn(List.of("100")))
                .thenReturn(List.of(existing));
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(1L)))
                .thenReturn(List.of());
        when(festivalRepository.markEndedBefore(
                eq(syncDate),
                eq(FestivalStatus.ENDED),
                eq(FestivalStatus.HIDDEN),
                any(OffsetDateTime.class)
        )).thenReturn(0);
        FestivalSyncWriter writer = new FestivalSyncWriter(
                festivalRepository,
                festivalImageRepository
        );

        FestivalSyncWriteResult result = writer.upsert(
                List.of(syncDataWithImage("100", "변경 축제")),
                syncDate
        );

        assertThat(result.synchronizedImageCount()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FestivalImage>> imageCaptor = ArgumentCaptor.forClass(List.class);
        verify(festivalImageRepository).saveAll(imageCaptor.capture());
        verify(festivalImageRepository).flush();
        assertThat(imageCaptor.getValue())
                .singleElement()
                .satisfies(image -> {
                    assertThat(image.getFestivalId()).isEqualTo(1L);
                    assertThat(image.getOriginImageUrl()).isEqualTo("https://example.com/origin.jpg");
                    assertThat(image.getThumbnailUrl()).isEqualTo("https://example.com/thumbnail.jpg");
                    assertThat(image.getDisplayOrder()).isZero();
                });
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
                null,
                null,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", contentId)
        );
    }

    private FestivalSyncData syncDataWithImage(String contentId, String title) {
        FestivalSyncData data = syncData(contentId, title);
        return new FestivalSyncData(
                data.contentId(),
                data.contentTypeId(),
                data.title(),
                data.address(),
                data.regionCode(),
                data.sigunguCode(),
                data.eventStartDate(),
                data.eventEndDate(),
                data.mapX(),
                data.mapY(),
                "https://example.com/origin.jpg",
                "https://example.com/thumbnail.jpg",
                data.syncedAt(),
                data.rawData()
        );
    }
}
