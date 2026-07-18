package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FestivalQueryServiceTest {

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private FestivalImageRepository festivalImageRepository;

    @Test
    void ACTIVE이면서_종료되지_않은_축제를_대표_이미지와_페이지_정보로_조회한다() {
        Festival festival = Festival.create(syncData(), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(festival, "id", 10L);
        FestivalImage image = FestivalImage.representative(
                festival,
                "https://example.com/origin.jpg",
                "https://example.com/thumbnail.jpg"
        );
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(festivalRepository.findVisibleFestivals(
                eq(FestivalStatus.ACTIVE),
                any(LocalDate.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(festival), pageRequest, 21));
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(10L)))
                .thenReturn(List.of(image));
        FestivalQueryService service = new FestivalQueryService(
                festivalRepository,
                festivalImageRepository
        );

        var result = service.getActiveFestivals(0, 20);

        assertThat(result.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(10L);
                    assertThat(item.title()).isEqualTo("테스트 축제");
                    assertThat(item.status()).isEqualTo(FestivalStatus.ACTIVE);
                    assertThat(item.originImageUrl()).isEqualTo("https://example.com/origin.jpg");
                    assertThat(item.thumbnailUrl()).isEqualTo("https://example.com/thumbnail.jpg");
                });
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
        verify(festivalImageRepository).findAllByFestivalIdIn(List.of(10L));
    }

    private FestivalSyncData syncData() {
        return new FestivalSyncData(
                "100",
                "15",
                "테스트 축제",
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
                Map.of("contentid", "100")
        );
    }
}
