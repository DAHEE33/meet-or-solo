package com.survey.meetorsolo.domain.tourplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.content.engagement.service.ContentEngagementSummaryReader;
import com.survey.meetorsolo.domain.content.support.ContentTargetType;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalImageRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceDetailResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListSort;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository.TourPlaceListProjection;
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

    /** 기본 동작(빈 Set/Map 반환)이면 "아무것도 찜하지 않은 열람자"와 같다. */
    @Mock
    private ContentEngagementSummaryReader engagementSummaries;

    private TourPlaceQueryService service() {
        return new TourPlaceQueryService(
                tourPlaceRepository, festivalRepository, festivalImageRepository, engagementSummaries);
    }

    @Test
    void ACTIVE_관광지를_페이지_정보와_함께_조회한다() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(tourPlaceRepository.findVisiblePlaces(
                eq(TourPlaceStatus.ACTIVE.name()),
                isNull(),
                isNull(),
                eq(""),
                eq("TITLE_ASC"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(projection(9L, 4L)), pageRequest, 1));

        TourPlaceListResponse result = service().getVisiblePlaces(0, 20, null, null, null, null, null);

        assertThat(result.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(1L);
                    assertThat(item.title()).isEqualTo("테스트 관광지");
                    assertThat(item.status()).isEqualTo(TourPlaceStatus.ACTIVE);
                    // 목록 카드가 오른쪽에 표시하는 좋아요(찜)·후기(댓글) 수.
                    assertThat(item.bookmarkCount()).isEqualTo(9L);
                    assertThat(item.commentCount()).isEqualTo(4L);
                });
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void keyword와_contentTypeId는_트림_후_전달되고_공백만_있으면_null로_전달한다() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(tourPlaceRepository.findVisiblePlaces(
                eq(TourPlaceStatus.ACTIVE.name()),
                eq("12"),
                isNull(),
                eq("관광지"),
                eq("TITLE_ASC"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        service().getVisiblePlaces(0, 20, "  12  ", "  관광지  ", null, null, null);

        verify(tourPlaceRepository).findVisiblePlaces(
                eq(TourPlaceStatus.ACTIVE.name()), eq("12"), isNull(), eq("관광지"),
                eq("TITLE_ASC"), any(Pageable.class));
    }

    @Test
    void contentTypeId가_공백뿐이면_null로_keyword가_공백뿐이면_빈_문자열로_전달한다() {
        // postgres가 lower(concat('%', :keyword, '%'))에서 null 파라미터의 타입을 추론하지 못해
        // (bytea로 오판) 오류가 나므로, keyword는 null 대신 빈 문자열을 넘겨 항상 LIKE 패턴이 적용되게 한다.
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(tourPlaceRepository.findVisiblePlaces(
                eq(TourPlaceStatus.ACTIVE.name()),
                isNull(),
                isNull(),
                eq(""),
                eq("TITLE_ASC"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        service().getVisiblePlaces(0, 20, "   ", "   ", null, null, null);

        verify(tourPlaceRepository).findVisiblePlaces(
                eq(TourPlaceStatus.ACTIVE.name()), isNull(), isNull(), eq(""),
                eq("TITLE_ASC"), any(Pageable.class));
    }

    @Test
    void sort가_null이면_기존_동작과_같은_제목_오름차순을_쓴다() {
        // 신규 파라미터를 안 넘긴 클라이언트가 이전과 같은 결과를 받아야 한다(회귀 방지).
        stubEmptyPage();

        service().getVisiblePlaces(0, 20, null, null, null, null, null);

        verify(tourPlaceRepository).findVisiblePlaces(
                any(), any(), any(), any(), eq("TITLE_ASC"), any(Pageable.class));
    }

    @Test
    void sort가_주어지면_그_이름을_쿼리에_넘긴다() {
        // 좋아요·후기 정렬 키는 엔티티 속성이 아니라 집계 값이라 Pageable의 Sort가 아니라
        // 쿼리 파라미터로 전달된다.
        stubEmptyPage();

        service().getVisiblePlaces(0, 20, null, null, null, TourPlaceListSort.BOOKMARK_COUNT_DESC, null);

        verify(tourPlaceRepository).findVisiblePlaces(
                any(), any(), any(), any(), eq("BOOKMARK_COUNT_DESC"), any(Pageable.class));
    }

    @Test
    void sigunguCode는_트림_후_전달되고_공백뿐이면_null로_전달한다() {
        stubEmptyPage();
        TourPlaceQueryService service = service();

        service.getVisiblePlaces(0, 20, null, null, "  150  ", null, null);
        service.getVisiblePlaces(0, 20, null, null, "   ", null, null);

        verify(tourPlaceRepository).findVisiblePlaces(any(), any(), eq("150"), any(), any(), any());
        verify(tourPlaceRepository).findVisiblePlaces(any(), any(), isNull(), any(), any(), any());
    }

    @Test
    void 지역_목록은_데이터에_있는_시군구만_이름과_함께_반환한다() {
        when(tourPlaceRepository.aggregateVisibleRegions(eq(TourPlaceStatus.ACTIVE), isNull()))
                .thenReturn(List.of(
                        new RegionAggregate("760", "강원특별자치도 평창군 대관령면 1", 523L),
                        new RegionAggregate("150", "강원특별자치도 강릉시 창해로 514", 782L),
                        new RegionAggregate("999", null, 1L)
                ));

        var regions = service().getTourPlaceRegions(null);

        assertThat(regions).extracting(RegionOptionResponse::name)
                .containsExactly("강릉시", "평창군");
        assertThat(regions).extracting(RegionOptionResponse::sigunguCode)
                .doesNotContain("999");
    }

    @Test
    void 지역_목록_조회에_contentTypeId를_트림해서_전달한다() {
        when(tourPlaceRepository.aggregateVisibleRegions(eq(TourPlaceStatus.ACTIVE), eq("39")))
                .thenReturn(List.of());

        service().getTourPlaceRegions("  39  ");

        verify(tourPlaceRepository).aggregateVisibleRegions(TourPlaceStatus.ACTIVE, "39");
    }

    private void stubEmptyPage() {
        when(tourPlaceRepository.findVisiblePlaces(
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    private TourPlaceListProjection projection(long bookmarkCount, long commentCount) {
        return new ProjectionRow(
                1L, "100", "12", "테스트 관광지", "강원특별자치도 테스트시",
                TourPlaceStatus.ACTIVE.name(), null, bookmarkCount, commentCount
        );
    }

    /**
     * native query 결과는 인터페이스 프로젝션이라 테스트에서 직접 만들 수 없다. 값만 돌려주는
     * record로 대신한다.
     */
    private record ProjectionRow(
            Long id,
            String contentId,
            String contentTypeId,
            String title,
            String address,
            String status,
            String imageUrl,
            long bookmarkCount,
            long commentCount
    ) implements TourPlaceListProjection {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getContentId() {
            return contentId;
        }

        @Override
        public String getContentTypeId() {
            return contentTypeId;
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
        public String getStatus() {
            return status;
        }

        @Override
        public String getImageUrl() {
            return imageUrl;
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
        when(festivalRepository.findAllVisibleWithinBoundingBox(
                eq(FestivalStatus.ACTIVE), any(LocalDate.class),
                any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), any(BigDecimal.class)
        )).thenReturn(List.of(far, near));
        // 이미지·집계 조회는 반경과 개수 제한을 모두 끝낸 뒤 최종 목록에 대해서만 한다.
        // bounding box 후보(먼 축제 포함)로 조회하면 버려질 행까지 읽게 된다.
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(10L))).thenReturn(List.of());

        var result = service().getNearbyFestivals(1L, 5000, 10, null);

        assertThat(result)
                .extracting("title")
                .containsExactly("가까운 축제");
        verify(engagementSummaries).summarize(ContentTargetType.FESTIVAL, List.of(10L), null);
    }

    @Test
    void 주변_축제_카드에_찜_수와_내_찜_여부를_함께_내려준다() {
        // 관광지 상세의 "주변에서 열리는 축제" 카드에서 바로 찜을 토글하므로 필요하다.
        TourPlace place = TourPlace.create(placeSyncData(
                "100", "테스트 관광지", new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));
        ReflectionTestUtils.setField(place, "id", 1L);
        when(tourPlaceRepository.findById(1L)).thenReturn(Optional.of(place));

        Festival near = Festival.create(festivalSyncData(
                "200", "가까운 축제", new BigDecimal("128.0010000000"), new BigDecimal("37.0000000000")
        ), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(near, "id", 10L);
        when(festivalRepository.findAllVisibleWithinBoundingBox(
                eq(FestivalStatus.ACTIVE), any(LocalDate.class),
                any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), any(BigDecimal.class)
        )).thenReturn(List.of(near));
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(10L))).thenReturn(List.of());
        when(engagementSummaries.summarize(ContentTargetType.FESTIVAL, List.of(10L), 7L))
                .thenReturn(Map.of(10L, new ContentEngagementSummaryReader.Summary(4, 2, true)));

        var result = service().getNearbyFestivals(1L, 5000, 10, 7L);

        assertThat(result).singleElement().satisfies(festival -> {
            assertThat(festival.bookmarkCount()).isEqualTo(4);
            assertThat(festival.commentCount()).isEqualTo(2);
            assertThat(festival.bookmarkedByMe()).isTrue();
        });
    }

    @Test
    void 집계가_없는_주변_축제는_0건과_찜하지_않음으로_내려간다() {
        // summarize는 찜·댓글이 하나도 없는 대상을 결과에 넣지 않는다. 호출부가 비워야 한다.
        TourPlace place = TourPlace.create(placeSyncData(
                "100", "테스트 관광지", new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));
        ReflectionTestUtils.setField(place, "id", 1L);
        when(tourPlaceRepository.findById(1L)).thenReturn(Optional.of(place));

        Festival near = Festival.create(festivalSyncData(
                "200", "가까운 축제", new BigDecimal("128.0010000000"), new BigDecimal("37.0000000000")
        ), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(near, "id", 10L);
        when(festivalRepository.findAllVisibleWithinBoundingBox(
                eq(FestivalStatus.ACTIVE), any(LocalDate.class),
                any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), any(BigDecimal.class)
        )).thenReturn(List.of(near));
        when(festivalImageRepository.findAllByFestivalIdIn(List.of(10L))).thenReturn(List.of());

        var result = service().getNearbyFestivals(1L, 5000, 10, null);

        assertThat(result).singleElement().satisfies(festival -> {
            assertThat(festival.bookmarkCount()).isZero();
            assertThat(festival.commentCount()).isZero();
            assertThat(festival.bookmarkedByMe()).isFalse();
        });
    }

    private com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData placeSyncData(
            String contentId, String title, BigDecimal mapX, BigDecimal mapY
    ) {
        return new com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData(
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
