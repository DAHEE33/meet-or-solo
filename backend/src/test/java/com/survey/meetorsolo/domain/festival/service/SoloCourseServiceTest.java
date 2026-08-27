package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.dto.SoloCourseResponse;
import com.survey.meetorsolo.domain.festival.dto.SoloCourseType;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlace;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.repository.TourPlaceRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SoloCourseServiceTest {

    private static final BigDecimal FESTIVAL_LAT = new BigDecimal("37.0000000000");
    private static final BigDecimal FESTIVAL_LON = new BigDecimal("128.0000000000");
    // 위도 37도 부근에서 경도 1도 ≈ 88,900m. 작은 거리(수백m)에서는 직선 근사로 충분히 정확하다.
    private static final double METERS_PER_DEGREE_LON = 88_900;

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private TourPlaceRepository tourPlaceRepository;

    private final SoloCourseStayPolicy stayPolicy = new SoloCourseStayPolicy();

    private SoloCourseService service() {
        return new SoloCourseService(festivalRepository, tourPlaceRepository, stayPolicy);
    }

    @Test
    void 좌표_없는_축제는_빈_코스를_반환한다() {
        Festival festival = Festival.create(syncData(), LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.HALF);

        assertThat(result.stops()).isEmpty();
        assertThat(result.totalDurationMinutes()).isZero();
    }

    @Test
    void HIDDEN_축제는_NOT_FOUND_예외를_던진다() {
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 11L);
        ReflectionTestUtils.setField(festival, "status", FestivalStatus.HIDDEN);
        when(festivalRepository.findById(11L)).thenReturn(Optional.of(festival));

        assertThatThrownBy(() -> service().getSoloCourse(11L, SoloCourseType.HALF))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void 반경_내_후보가_없으면_빈_코스를_반환한다() {
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        TourPlace tooFar = placeEastOfFestival(6_000, "12", 1L);
        when(tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE))
                .thenReturn(List.of(tooFar));

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.HALF);

        assertThat(result.stops()).isEmpty();
    }

    @Test
    void 축제에서_출발해_최근접_이웃_순서로_이어붙인다() {
        // A(100m 동쪽), B(150m 동쪽), C(120m 서쪽) — 축제 기준 거리순은 A,C,B지만
        // 최근접 이웃 순서는 A에서 B가 C보다 가까워 A,B,C가 되어야 한다.
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        TourPlace a = placeEastOfFestival(100, "12", 1L);
        TourPlace b = placeEastOfFestival(150, "14", 2L);
        TourPlace c = placeWestOfFestival(120, "39", 3L);
        when(tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE))
                .thenReturn(new ArrayList<>(List.of(c, b, a)));

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.FULL);

        assertThat(result.stops()).extracting("id").containsExactly(1L, 2L, 3L);
        assertThat(result.stops()).extracting("order").containsExactly(1, 2, 3);
    }

    @Test
    void 한번의_이동이_최대_이동거리를_넘으면_그_라운드에서_제외한다() {
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        // 반경(5,000m) 안이지만 MAX_HOP_METERS(1,500m)보다 멀어 첫 이동부터 제외되어야 한다.
        TourPlace tooFarHop = placeEastOfFestival(2_000, "12", 1L);
        when(tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE))
                .thenReturn(List.of(tooFarHop));

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.FULL);

        assertThat(result.stops()).isEmpty();
    }

    @Test
    void 최대_스톱_개수를_넘기지_않는다() {
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        List<TourPlace> places = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            places.add(placeEastOfFestival(i * 10, "14", (long) i));
        }
        when(tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE))
                .thenReturn(places);

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.FULL);

        assertThat(result.stops()).hasSize(SoloCourseStayPolicy.MAX_STOPS);
    }

    @Test
    void 예산을_넘기는_다음_후보는_건너뛰고_코스를_종료한다() {
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        // 액티비티(90분)만 3개: HALF(240분) 예산으로는 2개(약 182분)까지만 들어가고
        // 3번째(91분 추가, 총 273분)는 예산 초과라 제외되어야 한다.
        TourPlace first = placeEastOfFestival(50, "28", 1L);
        TourPlace second = placeEastOfFestival(100, "28", 2L);
        TourPlace third = placeEastOfFestival(150, "28", 3L);
        when(tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE))
                .thenReturn(List.of(third, second, first));

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.HALF);

        assertThat(result.stops()).extracting("id").containsExactly(1L, 2L);
        assertThat(result.totalDurationMinutes()).isLessThanOrEqualTo(240);
    }

    @Test
    void 직전과_같은_카테고리면_허용_범위_내_다른_카테고리_대안을_선택한다() {
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        TourPlace first = placeEastOfFestival(50, "14", 1L); // 1번째 스톱, 카테고리 14
        // 1번째 스톱 기준: sameCategory는 100m, diffCategory는 140m(1.4배, 1.5배 이내) 떨어져 있다.
        TourPlace sameCategory = placeEastOfFestival(150, "14", 2L);
        TourPlace diffCategory = placeEastOfFestival(190, "12", 3L);
        when(tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE))
                .thenReturn(List.of(diffCategory, sameCategory, first));

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.FULL);

        // 2번째 스톱은 대안(diffCategory, id=3)을 선택하고, 3번째 스톱은 남은 후보(sameCategory, id=2)를
        // 그대로 이어붙인다(직전 스톱과 카테고리가 다르므로 다양성 규칙이 적용되지 않는다).
        assertThat(result.stops()).extracting("id").containsExactly(1L, 3L, 2L);
    }

    @Test
    void 허용_범위_밖에만_다른_카테고리가_있으면_원래_가장_가까운_후보를_선택한다() {
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        TourPlace first = placeEastOfFestival(50, "14", 1L); // 1번째 스톱, 카테고리 14
        // 1번째 스톱 기준: sameCategory는 100m, diffCategory는 170m(1.7배, 1.5배 밖)로 대안이 될 수 없다.
        TourPlace sameCategory = placeEastOfFestival(150, "14", 2L);
        TourPlace diffCategory = placeEastOfFestival(220, "12", 3L);
        when(tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE))
                .thenReturn(List.of(diffCategory, sameCategory, first));

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.FULL);

        assertThat(result.stops()).extracting("id").containsExactly(1L, 2L, 3L);
    }

    @Test
    void 첫_스톱에는_카테고리_연속_방지_규칙을_적용하지_않는다() {
        Festival festival = festivalAt(FESTIVAL_LAT, FESTIVAL_LON);
        ReflectionTestUtils.setField(festival, "id", 10L);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        TourPlace onlyCandidate = placeEastOfFestival(50, "14", 1L);
        when(tourPlaceRepository.findAllVisibleWithCoordinates(TourPlaceStatus.ACTIVE))
                .thenReturn(List.of(onlyCandidate));

        SoloCourseResponse result = service().getSoloCourse(10L, SoloCourseType.FULL);

        assertThat(result.stops()).extracting("id").containsExactly(1L);
    }

    private TourPlace placeEastOfFestival(double meters, String contentTypeId, long id) {
        return placeAtOffset(meters, contentTypeId, id);
    }

    private TourPlace placeWestOfFestival(double meters, String contentTypeId, long id) {
        return placeAtOffset(-meters, contentTypeId, id);
    }

    private TourPlace placeAtOffset(double eastMeters, String contentTypeId, long id) {
        BigDecimal lon = FESTIVAL_LON
                .add(BigDecimal.valueOf(eastMeters / METERS_PER_DEGREE_LON))
                .setScale(10, RoundingMode.HALF_UP);
        TourPlace place = TourPlace.create(new TourPlaceSyncData(
                "content-" + id,
                contentTypeId,
                "테스트 관광지 " + id,
                "강원특별자치도 테스트시",
                "51",
                "110",
                lon,
                FESTIVAL_LAT,
                null,
                null,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", "content-" + id)
        ));
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private Festival festivalAt(BigDecimal lat, BigDecimal lon) {
        FestivalSyncData data = syncData();
        return Festival.create(new FestivalSyncData(
                data.contentId(),
                data.contentTypeId(),
                data.title(),
                data.address(),
                data.regionCode(),
                data.sigunguCode(),
                data.eventStartDate(),
                data.eventEndDate(),
                lon,
                lat,
                data.originImageUrl(),
                data.thumbnailUrl(),
                data.syncedAt(),
                data.rawData()
        ), LocalDate.of(2026, 7, 18));
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
