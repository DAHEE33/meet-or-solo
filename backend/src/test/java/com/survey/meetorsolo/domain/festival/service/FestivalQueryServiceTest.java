package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.content.engagement.service.ContentEngagementSummaryReader;
import com.survey.meetorsolo.domain.content.support.ContentTargetType;
import com.survey.meetorsolo.domain.festival.dto.FestivalDetailInfo;
import com.survey.meetorsolo.domain.festival.dto.FestivalDetailResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalInfoItem;
import com.survey.meetorsolo.domain.festival.dto.FestivalListSort;
import com.survey.meetorsolo.domain.festival.dto.FestivalProgressFilter;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalImage;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository.FestivalListProjection;
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

    /** 기본 동작(빈 Set 반환)이면 "아무것도 찜하지 않은 열람자"와 같다. */
    @Mock
    private ContentEngagementSummaryReader engagementSummaries;

    private FestivalQueryService service() {
        return new FestivalQueryService(
                festivalRepository,
                festivalImageRepository,
                tourPlaceRepository,
                festivalDetailInfoService,
                engagementSummaries
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
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(festivalRepository.findVisibleFestivals(
                eq(0),
                any(LocalDate.class),
                eq(""),
                isNull(),
                isNull(),
                isNull(),
                eq("ALL"),
                eq("RECENTLY_ADDED"),
                eq(0),
                eq(FestivalMeetingPointStatus.ACTIVE.name()),
                any(Pageable.class)
                )).thenReturn(new PageImpl<>(List.of(projection(7L, 3L)), pageRequest, 21));
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(10L)))
                .thenReturn(List.of(image));
        var result = service().getActiveFestivals(0, 20, null, null, null, null, null, null, false, null);

        assertThat(result.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(10L);
                    assertThat(item.title()).isEqualTo("테스트 축제");
                    assertThat(item.status()).isEqualTo(FestivalStatus.ACTIVE);
                    assertThat(item.originImageUrl()).isEqualTo("https://example.com/origin.jpg");
                    assertThat(item.thumbnailUrl()).isEqualTo("https://example.com/thumbnail.jpg");
                    // 목록 카드가 오른쪽에 표시하는 좋아요(찜)·후기(댓글) 수다.
                    assertThat(item.bookmarkCount()).isEqualTo(7L);
                    assertThat(item.commentCount()).isEqualTo(3L);
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
        stubEmptyPage();
        FestivalQueryService service = service();

        service.getActiveFestivals(0, 20, "  축제  ", null, null, null, null, null, false, null);
        service.getActiveFestivals(0, 20, "   ", null, null, null, null, null, false, null);

        verify(festivalRepository).findVisibleFestivals(
                anyInt(), any(), eq("축제"), any(), any(), any(), any(), any(), anyInt(), any(), any());
        verify(festivalRepository).findVisibleFestivals(
                anyInt(), any(), eq(""), any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void 아무_필터도_없으면_기존_가시성과_기본_정렬을_쓴다() {
        // 신규 파라미터를 아무것도 안 넘긴 클라이언트(홈 화면·관광지 상세)가 종료된 축제를
        // 받으면 안 된다 — includeEnded가 0이어야 기존 동작이 그대로 유지된다(회귀 방지).
        stubEmptyPage();

        service().getActiveFestivals(0, 20, null, null, null, null, null, null, false, null);

        verify(festivalRepository).findVisibleFestivals(
                eq(0), any(LocalDate.class), eq(""), isNull(),
                isNull(), isNull(), eq("ALL"), eq("RECENTLY_ADDED"), eq(0),
                eq(FestivalMeetingPointStatus.ACTIVE.name()), any(Pageable.class)
        );
    }

    @Test
    void sort가_주어지면_그_이름을_쿼리에_넘긴다() {
        // 좋아요·후기 정렬 키는 엔티티 속성이 아니라 집계 값이라 Pageable의 Sort가 아니라
        // 쿼리 파라미터로 전달된다.
        stubEmptyPage();

        service().getActiveFestivals(
                0, 20, null, null, FestivalListSort.BOOKMARK_COUNT_DESC, null, null, null, false, null);

        verify(festivalRepository).findVisibleFestivals(
                anyInt(), any(), any(), any(), any(), any(), any(),
                eq("BOOKMARK_COUNT_DESC"), anyInt(), any(), any());
    }

    @Test
    void progress를_넘기면_종료된_축제까지_조회하도록_가시성을_넓힌다() {
        // ENDED 축제는 기본 목록에서 보이지 않는다. "진행 마감" 검색은 이 플래그로만 열린다.
        stubEmptyPage();

        service().getActiveFestivals(
                0, 20, null, null, null, null, null, FestivalProgressFilter.ENDED, false, null);

        verify(festivalRepository).findVisibleFestivals(
                eq(1), any(), any(), any(), any(), any(), eq("ENDED"), any(), anyInt(), any(), any());
    }

    @Test
    void 선택한_기간은_그대로_기간_조건으로_넘어간다() {
        stubEmptyPage();

        service().getActiveFestivals(
                0, 20, null, null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                FestivalProgressFilter.ALL, false, null);

        verify(festivalRepository).findVisibleFestivals(
                anyInt(), any(), any(), any(),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)),
                any(), any(), anyInt(), any(), any());
    }

    @Test
    void matchableOnly가_true이면_만남장소_조건_플래그를_1로_넘긴다() {
        // 쿼리에서 boolean 파라미터 타입 추론이 흔들리는 것을 피하려 int 플래그를 쓴다.
        stubEmptyPage();

        service().getActiveFestivals(0, 20, null, null, null, null, null, null, true, null);

        verify(festivalRepository).findVisibleFestivals(
                eq(0), any(LocalDate.class), eq(""), isNull(),
                isNull(), isNull(), eq("ALL"), eq("RECENTLY_ADDED"), eq(1),
                eq(FestivalMeetingPointStatus.ACTIVE.name()), any(Pageable.class)
        );
    }

    @Test
    void sigunguCode는_트림_후_전달되고_공백뿐이면_null로_전달한다() {
        stubEmptyPage();
        FestivalQueryService service = service();

        service.getActiveFestivals(0, 20, null, "  110  ", null, null, null, null, false, null);
        service.getActiveFestivals(0, 20, null, "   ", null, null, null, null, false, null);

        verify(festivalRepository).findVisibleFestivals(
                anyInt(), any(), any(), eq("110"), any(), any(), any(), any(), anyInt(), any(), any());
        verify(festivalRepository).findVisibleFestivals(
                anyInt(), any(), any(), isNull(), any(), any(), any(), any(), anyInt(), any(), any());
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
                anyInt(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    private FestivalListProjection projection(long bookmarkCount, long commentCount) {
        return new ProjectionRow(
                10L, "100", "테스트 축제", "강원특별자치도 테스트시", "51", "110",
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 22), FestivalStatus.ACTIVE.name(),
                new BigDecimal("127.7300000000"), new BigDecimal("37.8813000000"),
                bookmarkCount, commentCount
        );
    }

    /**
     * native query 결과는 인터페이스 프로젝션이라 테스트에서 직접 만들 수 없다. 값만 돌려주는
     * record로 대신한다.
     */
    private record ProjectionRow(
            Long id,
            String contentId,
            String title,
            String address,
            String regionCode,
            String sigunguCode,
            LocalDate eventStartDate,
            LocalDate eventEndDate,
            String status,
            BigDecimal mapX,
            BigDecimal mapY,
            long bookmarkCount,
            long commentCount
    ) implements FestivalListProjection {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getContentId() {
            return contentId;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getAddress() {
            return address;
        }

        @Override
        public String getRegionCode() {
            return regionCode;
        }

        @Override
        public String getSigunguCode() {
            return sigunguCode;
        }

        @Override
        public LocalDate getEventStartDate() {
            return eventStartDate;
        }

        @Override
        public LocalDate getEventEndDate() {
            return eventEndDate;
        }

        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public BigDecimal getMapX() {
            return mapX;
        }

        @Override
        public BigDecimal getMapY() {
            return mapY;
        }

        @Override
        public long getBookmarkCount() {
            return bookmarkCount;
        }

        @Override
        public long getCommentCount() {
            return commentCount;
        }
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

        var result = service().getNearbyTourPlaces(10L, 5000, 10, null);

        assertThat(result)
                .extracting("title")
                .containsExactly("가까운 관광지");
        // 집계는 반경·정렬·개수 제한을 끝낸 뒤 최종 목록(가까운 관광지 1건)에만 한다 —
        // bounding box 후보인 "먼 관광지"까지 세면 버려질 행을 읽는 것이다.
        verify(engagementSummaries).summarize(ContentTargetType.TOUR_PLACE, List.of(1L), null);
    }

    @Test
    void 축제_주변_관광지에_찜_수와_내_찜_여부를_함께_내려준다() {
        // 홈 "축제와 함께 둘러보기" 카드에서 바로 찜을 토글하므로 필요하다.
        // 좌표가 없는 축제는 반경 검색이 빈 목록으로 조기 반환하므로 좌표 있는 fixture를 쓴다.
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
        when(tourPlaceRepository.findAllVisibleWithinBoundingBox(
                eq(TourPlaceStatus.ACTIVE),
                any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), any(BigDecimal.class)
        )).thenReturn(List.of(near));
        when(engagementSummaries.summarize(ContentTargetType.TOUR_PLACE, List.of(1L), 7L))
                .thenReturn(Map.of(1L, new ContentEngagementSummaryReader.Summary(3, 1, true)));

        var result = service().getNearbyTourPlaces(10L, 5000, 10, 7L);

        assertThat(result).singleElement().satisfies(place -> {
            assertThat(place.bookmarkCount()).isEqualTo(3);
            assertThat(place.commentCount()).isEqualTo(1);
            assertThat(place.bookmarkedByMe()).isTrue();
        });
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
