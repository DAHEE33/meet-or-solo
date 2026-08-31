package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.dto.FestivalDetailInfo;
import com.survey.meetorsolo.domain.festival.dto.FestivalDetailResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalInfoItem;
import com.survey.meetorsolo.domain.festival.dto.FestivalListSort;
import com.survey.meetorsolo.domain.festival.dto.FestivalScheduleFilter;
import com.survey.meetorsolo.domain.festival.dto.FestivalSummary;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.global.region.RegionAggregate;
import com.survey.meetorsolo.global.region.RegionOptionResponse;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private TourPlaceRepository tourPlaceRepository;

    @Mock
    private FestivalDetailInfoService festivalDetailInfoService;

    private FestivalQueryService service() {
        return new FestivalQueryService(
                festivalRepository,
                festivalImageRepository,
                tourPlaceRepository,
                festivalDetailInfoService
        );
    }

    @Test
    void ACTIVE이면서_종료되지_않은_축제를_대표_이미지와_페이지_정보로_조회한다() {
        Festival festival = Festival.create(syncData(), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(festival, "id", 10L);
        FestivalImage image = FestivalImage.representative(
                festival,
                "https://example.com/origin.jpg",
                "https://example.com/thumbnail.jpg"
        );
        FestivalSummary summary = new FestivalSummary(
                10L, "100", "테스트 축제", "강원특별자치도 테스트시", "51", "110",
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 22), FestivalStatus.ACTIVE,
                new BigDecimal("127.7300000000"), new BigDecimal("37.8813000000")
        );
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(festivalRepository.findVisibleFestivals(
                eq(FestivalStatus.ACTIVE),
                any(LocalDate.class),
                eq(""),
                isNull(),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(0),
                eq(FestivalMeetingPointStatus.ACTIVE),
                any(Pageable.class)
                )).thenReturn(new PageImpl<>(List.of(summary), pageRequest, 21));
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(10L)))
                .thenReturn(List.of(image));
        var result = service().getActiveFestivals(0, 20, null, null, null, null, false);

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

    @Test
    void keyword는_트림_후_전달되고_공백뿐이면_빈_문자열로_전달한다() {
        // postgres가 lower(concat('%', :keyword, '%'))에서 null 파라미터의 타입을 추론하지 못해
        // (bytea로 오판) 오류가 나므로, null 대신 빈 문자열을 넘겨 항상 LIKE 패턴이 적용되게 한다.
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(festivalRepository.findVisibleFestivals(
                eq(FestivalStatus.ACTIVE),
                any(LocalDate.class),
                eq("축제"),
                isNull(),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(0),
                eq(FestivalMeetingPointStatus.ACTIVE),
                any(Pageable.class)
                )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));
        when(festivalRepository.findVisibleFestivals(
                eq(FestivalStatus.ACTIVE),
                any(LocalDate.class),
                eq(""),
                isNull(),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(0),
                eq(FestivalMeetingPointStatus.ACTIVE),
                any(Pageable.class)
                )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));
        FestivalQueryService service = service();

        service.getActiveFestivals(0, 20, "  축제  ", null, null, null, false);
        service.getActiveFestivals(0, 20, "   ", null, null, null, false);

        verify(festivalRepository).findVisibleFestivals(eq(FestivalStatus.ACTIVE), any(LocalDate.class), eq("축제"), isNull(), any(LocalDate.class), any(LocalDate.class), eq(0), eq(FestivalMeetingPointStatus.ACTIVE), any(Pageable.class));
        verify(festivalRepository).findVisibleFestivals(eq(FestivalStatus.ACTIVE), any(LocalDate.class), eq(""), isNull(), any(LocalDate.class), any(LocalDate.class), eq(0), eq(FestivalMeetingPointStatus.ACTIVE), any(Pageable.class));
    }

    @Test
    void sort와_schedule이_null이면_기존_동작과_같은_기본값을_쓴다() {
        // 신규 파라미터를 아무것도 안 넘긴 클라이언트가 이전과 같은 결과를 받아야 한다(회귀 방지).
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        stubEmptyPage();

        service().getActiveFestivals(0, 20, null, null, null, null, false);

        verify(festivalRepository).findVisibleFestivals(
                eq(FestivalStatus.ACTIVE), any(LocalDate.class), eq(""), isNull(),
                any(LocalDate.class), eq(FestivalScheduleFilter.MAX_SCHEDULE_DATE), eq(0),
                eq(FestivalMeetingPointStatus.ACTIVE), pageable.capture()
        );
        assertThat(pageable.getValue().getSort())
                .isEqualTo(FestivalListSort.START_DATE_ASC.sort());
    }

    @Test
    void sort가_주어지면_해당_정렬로_페이지를_요청한다() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        stubEmptyPage();

        service().getActiveFestivals(
                0, 20, null, null, FestivalListSort.RECENTLY_ADDED, null, false);

        verify(festivalRepository).findVisibleFestivals(
                any(), any(), any(), any(), any(), any(), anyInt(), any(), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(FestivalListSort.RECENTLY_ADDED.sort());
    }

    @Test
    void matchableOnly가_true이면_만남장소_조건_플래그를_1로_넘긴다() {
        // JPQL에서 boolean 파라미터 타입 추론이 흔들리는 것을 피하려 int 플래그를 쓴다.
        stubEmptyPage();

        service().getActiveFestivals(0, 20, null, null, null, null, true);

        verify(festivalRepository).findVisibleFestivals(
                eq(FestivalStatus.ACTIVE), any(LocalDate.class), eq(""), isNull(),
                any(LocalDate.class), any(LocalDate.class), eq(1),
                eq(FestivalMeetingPointStatus.ACTIVE), any(Pageable.class)
        );
    }

    @Test
    void sigunguCode는_트림_후_전달되고_공백뿐이면_null로_전달한다() {
        stubEmptyPage();
        FestivalQueryService service = service();

        service.getActiveFestivals(0, 20, null, "  110  ", null, null, false);
        service.getActiveFestivals(0, 20, null, "   ", null, null, false);

        verify(festivalRepository).findVisibleFestivals(
                any(), any(), any(), eq("110"), any(), any(), anyInt(), any(), any());
        verify(festivalRepository).findVisibleFestivals(
                any(), any(), any(), isNull(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void schedule이_ONGOING이면_오늘_하루를_기간_조건으로_넘긴다() {
        ArgumentCaptor<LocalDate> start = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> end = ArgumentCaptor.forClass(LocalDate.class);
        stubEmptyPage();

        service().getActiveFestivals(
                0, 20, null, null, null, FestivalScheduleFilter.ONGOING, false);

        verify(festivalRepository).findVisibleFestivals(
                any(), any(), any(), any(), start.capture(), end.capture(), anyInt(), any(), any());
        assertThat(start.getValue()).isEqualTo(end.getValue());
    }

    @Test
    void 지역_목록은_데이터에_있는_시군구만_이름과_함께_반환한다() {
        when(festivalRepository.aggregateVisibleRegions(eq(FestivalStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(
                        new RegionAggregate("760", "강원특별자치도 평창군 대관령면 1", 4L),
                        new RegionAggregate("150", "강원특별자치도 강릉시 창해로 514", 3L),
                        new RegionAggregate("999", null, 1L)
                ));

        var regions = service().getFestivalRegions();

        assertThat(regions).extracting(RegionOptionResponse::name)
                .containsExactly("강릉시", "평창군");
        assertThat(regions).extracting(RegionOptionResponse::sigunguCode)
                .doesNotContain("999");
    }

    private void stubEmptyPage() {
        when(festivalRepository.findVisibleFestivals(
                any(), any(), any(), any(), any(), any(), anyInt(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    @Test
    void 축제_id로_상세를_조회한다() {
        Festival festival = Festival.create(syncData(), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(festival, "id", 10L);
        FestivalImage image = FestivalImage.representative(
                festival,
                "https://example.com/origin.jpg",
                "https://example.com/thumbnail.jpg"
        );
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(10L)))
                .thenReturn(List.of(image));
        when(festivalDetailInfoService.getDetailInfo("100", "15")).thenReturn(
                new FestivalDetailInfo("소개글", List.of(new FestivalInfoItem("주최", "테스트시")), List.of())
        );

        FestivalDetailResponse result = service().getFestivalDetail(10L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.title()).isEqualTo("테스트 축제");
        assertThat(result.status()).isEqualTo(FestivalStatus.ACTIVE);
        assertThat(result.originImageUrl()).isEqualTo("https://example.com/origin.jpg");
        assertThat(result.thumbnailUrl()).isEqualTo("https://example.com/thumbnail.jpg");
        assertThat(result.intro()).isEqualTo("소개글");
        assertThat(result.infoItems()).containsExactly(new FestivalInfoItem("주최", "테스트시"));
        assertThat(result.programs()).isEmpty();
    }

    @Test
    void 존재하지_않는_축제_id를_조회하면_NOT_FOUND_예외를_던진다() {
        when(festivalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getFestivalDetail(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void HIDDEN_상태_축제_상세_조회는_NOT_FOUND_예외를_던진다() {
        Festival festival = Festival.create(syncData(), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(festival, "id", 11L);
        ReflectionTestUtils.setField(festival, "status", FestivalStatus.HIDDEN);
        when(festivalRepository.findById(11L)).thenReturn(Optional.of(festival));

        assertThatThrownBy(() -> service().getFestivalDetail(11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void 축제_반경_내_관광지를_거리순으로_조회한다() {
        Festival festival = Festival.create(syncDataWithCoordinates(
                new BigDecimal("128.0000000000"),
                new BigDecimal("37.0000000000")
        ), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));

        TourPlace near = TourPlace.create(placeSyncData(
                "200", "가까운 관광지", new BigDecimal("128.0010000000"), new BigDecimal("37.0000000000")
        ));
        ReflectionTestUtils.setField(near, "id", 1L);
        TourPlace far = TourPlace.create(placeSyncData(
                "300", "먼 관광지", new BigDecimal("128.5000000000"), new BigDecimal("37.5000000000")
        ));
        ReflectionTestUtils.setField(far, "id", 2L);
        when(tourPlaceRepository.findAllVisibleWithinBoundingBox(
                eq(TourPlaceStatus.ACTIVE),
                any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), any(BigDecimal.class)
        )).thenReturn(List.of(far, near));

        var result = service().getNearbyTourPlaces(10L, 5000, 10);

        assertThat(result)
                .extracting("title")
                .containsExactly("가까운 관광지");
    }

    private TourPlaceSyncData placeSyncData(String contentId, String title, BigDecimal mapX, BigDecimal mapY) {
        return new TourPlaceSyncData(
                contentId,
                "12",
                title,
                "강원특별자치도 테스트시",
                "51",
                "110",
                mapX,
                mapY,
                null,
                null,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", contentId)
        );
    }

    private FestivalSyncData syncDataWithCoordinates(BigDecimal mapX, BigDecimal mapY) {
        FestivalSyncData data = syncData();
        return new FestivalSyncData(
                data.contentId(),
                data.contentTypeId(),
                data.title(),
                data.address(),
                data.regionCode(),
                data.sigunguCode(),
                data.eventStartDate(),
                data.eventEndDate(),
                mapX,
                mapY,
                data.originImageUrl(),
                data.thumbnailUrl(),
                data.syncedAt(),
                data.rawData()
        );
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
