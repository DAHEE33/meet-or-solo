package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
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

@ExtendWith(MockitoExtension.class)
class FestivalSyncWriterTest {

    @Mock
    private FestivalRepository festivalRepository;

    @Test
    void contentId를_기준으로_신규_축제와_기존_축제를_upsert한다() {
        LocalDate syncDate = LocalDate.of(2026, 7, 18);
        Festival existing = Festival.create(syncData("100", "이전 제목"), syncDate);
        when(festivalRepository.count()).thenReturn(1L);
        when(festivalRepository.findAllByContentIdIn(List.of("100", "200")))
                .thenReturn(List.of(existing));
        FestivalSyncWriter writer = new FestivalSyncWriter(festivalRepository);

        FestivalSyncWriteResult result = writer.upsert(
                List.of(syncData("100", "변경 제목"), syncData("200", "신규 축제")),
                syncDate
        );

        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
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
    void 최초_동기화_결과가_비어_있으면_DB를_변경하지_않는다() {
        when(festivalRepository.count()).thenReturn(0L);
        FestivalSyncWriter writer = new FestivalSyncWriter(festivalRepository);

        FestivalSyncWriteResult result = writer.upsert(List.of(), LocalDate.of(2026, 7, 18));

        assertThat(result.initialLoad()).isTrue();
        assertThat(result.insertedCount()).isZero();
        verify(festivalRepository, never()).findAllByContentIdIn(anyList());
        verify(festivalRepository, never()).saveAll(anyList());
        verify(festivalRepository, never()).flush();
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
                Map.of("contentid", contentId)
        );
    }
}
