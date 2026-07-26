package com.survey.meetorsolo.domain.tourplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceDetailResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListResponse;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TourPlaceQueryServiceTest {

    @Mock
    private TourPlaceRepository tourPlaceRepository;

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private FestivalImageRepository festivalImageRepository;

    private TourPlaceQueryService service() {
        return new TourPlaceQueryService(tourPlaceRepository, festivalRepository, festivalImageRepository);
    }

    @Test
    void ACTIVE_관광지를_페이지_정보와_함께_조회한다() {
        TourPlace place = TourPlace.create(placeSyncData("100", "테스트 관광지", null, null));
        ReflectionTestUtils.setField(place, "id", 1L);
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(tourPlaceRepository.findVisiblePlaces(
                eq(TourPlaceStatus.ACTIVE),
                isNull(),
                eq(""),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(place), pageRequest, 1));

        TourPlaceListResponse result = service().getVisiblePlaces(0, 20, null, null);

        assertThat(result.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(1L);
                    assertThat(item.title()).isEqualTo("테스트 관광지");
                });
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void keyword와_contentTypeId는_트림_후_전달되고_공백만_있으면_null로_전달한다() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(tourPlaceRepository.findVisiblePlaces(
                eq(TourPlaceStatus.ACTIVE),
                eq("12"),
                eq("관광지"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        service().getVisiblePlaces(0, 20, "  12  ", "  관광지  ");

        verify(tourPlaceRepository).findVisiblePlaces(eq(TourPlaceStatus.ACTIVE), eq("12"), eq("관광지"), any(Pageable.class));
    }

    @Test
    void contentTypeId가_공백뿐이면_null로_keyword가_공백뿐이면_빈_문자열로_전달한다() {
        // postgres가 lower(concat('%', :keyword, '%'))에서 null 파라미터의 타입을 추론하지 못해
        // (bytea로 오판) 오류가 나므로, keyword는 null 대신 빈 문자열을 넘겨 항상 LIKE 패턴이 적용되게 한다.
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(tourPlaceRepository.findVisiblePlaces(
                eq(TourPlaceStatus.ACTIVE),
                isNull(),
                eq(""),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        service().getVisiblePlaces(0, 20, "   ", "   ");

        verify(tourPlaceRepository).findVisiblePlaces(eq(TourPlaceStatus.ACTIVE), isNull(), eq(""), any(Pageable.class));
    }

    @Test
    void 관광지_id로_상세를_조회한다() {
        TourPlace place = TourPlace.create(placeSyncData("100", "테스트 관광지", null, null));
        ReflectionTestUtils.setField(place, "id", 1L);
        when(tourPlaceRepository.findById(1L)).thenReturn(Optional.of(place));

        TourPlaceDetailResponse result = service().getTourPlaceDetail(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("테스트 관광지");
    }

    @Test
    void 존재하지_않는_관광지_id를_조회하면_NOT_FOUND_예외를_던진다() {
        when(tourPlaceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getTourPlaceDetail(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void HIDDEN_상태_관광지_상세_조회는_NOT_FOUND_예외를_던진다() {
        TourPlace place = TourPlace.create(placeSyncData("100", "테스트 관광지", null, null));
        ReflectionTestUtils.setField(place, "id", 1L);
        ReflectionTestUtils.setField(place, "status", TourPlaceStatus.HIDDEN);
        when(tourPlaceRepository.findById(1L)).thenReturn(Optional.of(place));

        assertThatThrownBy(() -> service().getTourPlaceDetail(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 관광지_반경_내_축제를_거리순으로_조회한다() {
        TourPlace place = TourPlace.create(placeSyncData(
                "100", "테스트 관광지", new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));
        ReflectionTestUtils.setField(place, "id", 1L);
        when(tourPlaceRepository.findById(1L)).thenReturn(Optional.of(place));

        Festival near = Festival.create(festivalSyncData(
                "200", "가까운 축제", new BigDecimal("128.0010000000"), new BigDecimal("37.0000000000")
        ), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(near, "id", 10L);
        Festival far = Festival.create(festivalSyncData(
                "300", "먼 축제", new BigDecimal("128.5000000000"), new BigDecimal("37.5000000000")
        ), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(far, "id", 20L);
        when(festivalRepository.findAllVisibleWithCoordinates(eq(FestivalStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(far, near));
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(20L, 10L))).thenReturn(List.of());

        var result = service().getNearbyFestivals(1L, 5000, 10);

        assertThat(result)
                .extracting("title")
                .containsExactly("가까운 축제");
    }

    private com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData placeSyncData(
            String contentId, String title, BigDecimal mapX, BigDecimal mapY
    ) {
        return new com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData(
                contentId,
                "12",
                title,
                "강원특별자치도 테스트시",
                mapX,
                mapY,
                null,
                null,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", contentId)
        );
    }

    private FestivalSyncData festivalSyncData(String contentId, String title, BigDecimal mapX, BigDecimal mapY) {
        return new FestivalSyncData(
                contentId,
                "15",
                title,
                "강원특별자치도 테스트시",
                "51",
                "110",
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 22),
                mapX,
                mapY,
                null,
                null,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", contentId)
        );
    }
}
