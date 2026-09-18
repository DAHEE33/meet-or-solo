package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.config.FestivalCheckinProperties;
import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.CurrentCheckinResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckin;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import com.survey.meetorsolo.domain.festival.event.FestivalCheckinCancelledEvent;
import com.survey.meetorsolo.domain.festival.repository.FestivalCheckinRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FestivalCheckinServiceTest {

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private FestivalCheckinRepository festivalCheckinRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FestivalCheckinService service() {
        return service(false);
    }

    private FestivalCheckinService service(boolean bypassRadiusCheck) {
        return service(500, bypassRadiusCheck);
    }

    /** 반경을 바꿔가며 검증하는 테스트가 쓴다(FESTIVAL_CHECKIN_RADIUS_METERS, docs/32 3.1 후속). */
    private FestivalCheckinService service(int radiusMeters, boolean bypassRadiusCheck) {
        return new FestivalCheckinService(
                festivalRepository,
                festivalCheckinRepository,
                memberRepository,
                new FestivalCheckinProperties(radiusMeters, 100, bypassRadiusCheck),
                eventPublisher
        );
    }

    /** 테스트 계정 조회 결과를 고정한다. 설정 우회(bypassRadiusCheck)와 경로가 다르다. */
    private void testAccount(long memberId, boolean value) {
        when(memberRepository.existsByIdAndTestAccountIsTrue(memberId)).thenReturn(value);
    }

    @Test
    void 반경_이내면_체크인이_생성되고_거리가_계산된다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of());

        FestivalCheckinResponse result = service().checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.0010000000"), new BigDecimal("128.0000000000"), 20)
        );

        assertThat(result.festivalId()).isEqualTo(10L);
        assertThat(result.distanceMeters()).isGreaterThan(0);
        assertThat(result.status()).isEqualTo(FestivalCheckinStatus.ACTIVE);
        verify(festivalCheckinRepository).save(any(FestivalCheckin.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 반경을_벗어나면_CHECKIN_OUT_OF_RANGE_예외를_던진다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));

        assertThatThrownBy(() -> service().checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.5000000000"), new BigDecimal("128.5000000000"), 20)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.CHECKIN_OUT_OF_RANGE));
        verify(festivalCheckinRepository, never()).save(any());
    }

    /**
     * 순서가 바뀌면 이 검증이 깨진다. 실내·PC 측위는 정확도가 수백 m로 나오는 일이 흔해,
     * 정확도를 먼저 보면 <b>반경 밖에 있는 사람은 사실상 항상 "정확도가 낮다"만 보게 된다.</b>
     * 그러면 원인이 "현장에 없다"인데도 같은 자리에서 다시 누르게 된다(docs/32 3.1).
     */
    @Test
    void 반경_밖이면서_정확도도_낮으면_정확도가_아니라_CHECKIN_OUT_OF_RANGE를_던진다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));

        assertThatThrownBy(() -> service().checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.5000000000"), new BigDecimal("128.5000000000"), 900)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.CHECKIN_OUT_OF_RANGE));
        verify(festivalCheckinRepository, never()).save(any());
    }

    /** 사유만으로는 "GPS가 이상한 것"과 "내가 정말 먼 것"을 구분할 수 없어 거리를 함께 준다. */
    @Test
    void 반경_밖_거절_문구에_떨어진_거리와_반경이_들어간다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));

        assertThatThrownBy(() -> service().checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.5000000000"), new BigDecimal("128.5000000000"), 20)
        ))
                .isInstanceOf(BusinessException.class)
                // 기본 반경은 500m다. 거리는 km 단위로 환산돼 들어간다.
                .hasMessageContaining("km")
                .hasMessageContaining("500m");
    }

    /**
     * festivals.checkin_radius_meters(DB 컬럼)가 아니라 설정값이 실제 검증에 쓰인다.
     * 그 컬럼을 축제마다 다르게 채우는 코드가 없어 모든 축제가 항상 같은 값이었기
     * 때문이다(docs/32 3.1 후속, FESTIVAL_CHECKIN_RADIUS_METERS).
     */
    @Test
    void 반경은_축제_컬럼이_아니라_설정값을_따른다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of());
        // 약 600m 떨어진 좌표. 기본 500m라면 거절되지만, 반경을 1000m로 넓히면 통과해야 한다.
        CheckInRequest request = new CheckInRequest(
                new BigDecimal("37.0054000000"), new BigDecimal("128.0000000000"), 20);

        assertThatThrownBy(() -> service(500, false).checkIn(1L, 10L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.CHECKIN_OUT_OF_RANGE));

        FestivalCheckinResponse result = service(1_000, false).checkIn(1L, 10L, request);
        assertThat(result.status()).isEqualTo(FestivalCheckinStatus.ACTIVE);
    }

    @Test
    void 위치_정확도가_임계값을_넘으면_LOW_LOCATION_ACCURACY_예외를_던진다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));

        assertThatThrownBy(() -> service().checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), 150)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.LOW_LOCATION_ACCURACY));
        verify(festivalCheckinRepository, never()).save(any());
    }

    @Test
    void bypassRadiusCheck가_true면_반경을_벗어나도_체크인이_생성된다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of());

        FestivalCheckinResponse result = service(true).checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.5000000000"), new BigDecimal("128.5000000000"), 20)
        );

        assertThat(result.status()).isEqualTo(FestivalCheckinStatus.ACTIVE);
        verify(festivalCheckinRepository).save(any(FestivalCheckin.class));
    }

    @Test
    void bypassRadiusCheck가_true면_위치_정확도가_낮아도_체크인이_생성된다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of());

        FestivalCheckinResponse result = service(true).checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), 150)
        );

        assertThat(result.status()).isEqualTo(FestivalCheckinStatus.ACTIVE);
        verify(festivalCheckinRepository).save(any(FestivalCheckin.class));
    }

    @Test
    void 테스트_계정이면_설정이_꺼져_있어도_반경을_벗어나서_체크인할_수_있다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of());
        testAccount(1L, true);

        FestivalCheckinResponse result = service().checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.5000000000"), new BigDecimal("128.5000000000"), 20)
        );

        assertThat(result.status()).isEqualTo(FestivalCheckinStatus.ACTIVE);
        verify(festivalCheckinRepository).save(any(FestivalCheckin.class));
    }

    @Test
    void 테스트_계정이면_위치_정확도가_낮아도_체크인이_생성된다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of());
        testAccount(1L, true);

        FestivalCheckinResponse result = service().checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), 150)
        );

        assertThat(result.status()).isEqualTo(FestivalCheckinStatus.ACTIVE);
        verify(festivalCheckinRepository).save(any(FestivalCheckin.class));
    }

    /**
     * 면제 판정은 요청한 회원 자신의 표시로만 한다. 다른 회원이 테스트 계정이라는 사실은
     * 이 요청과 무관하다.
     */
    @Test
    void 테스트_계정이_아닌_회원은_반경_검증을_그대로_받는다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));
        testAccount(2L, false);

        assertThatThrownBy(() -> service().checkIn(
                2L, 10L,
                new CheckInRequest(new BigDecimal("37.5000000000"), new BigDecimal("128.5000000000"), 20)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.CHECKIN_OUT_OF_RANGE));
        verify(festivalCheckinRepository, never()).save(any());
    }

    @Test
    void 좌표가_없는_축제는_FESTIVAL_LOCATION_UNAVAILABLE_예외를_던진다() {
        Festival festival = festivalAt(10L, null, null);
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));

        assertThatThrownBy(() -> service().checkIn(
                1L, 10L,
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.FESTIVAL_LOCATION_UNAVAILABLE));
    }

    @Test
    void 존재하지_않는_축제는_NOT_FOUND_예외를_던진다() {
        when(festivalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().checkIn(
                1L, 99L,
                new CheckInRequest(new BigDecimal("37.0"), new BigDecimal("128.0"), null)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void 기존_ACTIVE_체크인은_다른_축제여도_새_체크인_시_취소된다() {
        Festival festival = festivalAt(20L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(20L)).thenReturn(Optional.of(festival));
        FestivalCheckin previous = FestivalCheckin.create(1L, 10L, 100, Duration.ofHours(6));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of(previous));

        service().checkIn(
                1L, 20L,
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        );

        assertThat(previous.getStatus()).isEqualTo(FestivalCheckinStatus.CANCELLED);
    }

    @Test
    void 기존_ACTIVE_체크인이_취소되면_취소된_축제_기준으로_FestivalCheckinCancelledEvent가_발행된다() {
        Festival festival = festivalAt(20L, new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000"));
        when(festivalRepository.findById(20L)).thenReturn(Optional.of(festival));
        FestivalCheckin previous = FestivalCheckin.create(1L, 10L, 100, Duration.ofHours(6));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of(previous));

        service().checkIn(
                1L, 20L,
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        );

        ArgumentCaptor<FestivalCheckinCancelledEvent> captor =
                ArgumentCaptor.forClass(FestivalCheckinCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().memberId()).isEqualTo(1L);
        // 새로 체크인한 축제(20L)가 아니라 취소된 기존 축제(10L) 기준으로 발행돼야 한다.
        assertThat(captor.getValue().festivalId()).isEqualTo(10L);
    }

    @Test
    void 활성_체크인이_없으면_getCurrentCheckin은_빈_값을_반환한다() {
        when(festivalCheckinRepository.findValidActiveCheckin(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        Optional<CurrentCheckinResponse> result = service().getCurrentCheckin(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void 활성_체크인이_있으면_getCurrentCheckin은_축제명과_함께_반환한다() {
        Festival festival = festivalAt(10L, new BigDecimal("128.0"), new BigDecimal("37.0"));
        FestivalCheckin checkin = FestivalCheckin.create(1L, 10L, 50, Duration.ofHours(1));
        when(festivalCheckinRepository.findValidActiveCheckin(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(checkin));
        when(festivalRepository.findById(10L)).thenReturn(Optional.of(festival));

        Optional<CurrentCheckinResponse> result = service().getCurrentCheckin(1L);

        assertThat(result).isPresent();
        assertThat(result.get().festivalId()).isEqualTo(10L);
        assertThat(result.get().festivalName()).isEqualTo("테스트 축제");
        assertThat(result.get().expiresAt()).isEqualTo(checkin.getExpiresAt());
    }

    @Test
    void 활성_체크인이_없으면_cancelCurrentCheckin은_NOT_FOUND_예외를_던진다() {
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service().cancelCurrentCheckin(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 활성_체크인이_있으면_cancelCurrentCheckin은_취소하고_이벤트를_발행한다() {
        FestivalCheckin active = FestivalCheckin.create(1L, 10L, 50, Duration.ofHours(1));
        when(festivalCheckinRepository.findAllByMemberIdAndStatus(1L, FestivalCheckinStatus.ACTIVE))
                .thenReturn(List.of(active));

        service().cancelCurrentCheckin(1L);

        assertThat(active.getStatus()).isEqualTo(FestivalCheckinStatus.CANCELLED);
        ArgumentCaptor<FestivalCheckinCancelledEvent> captor =
                ArgumentCaptor.forClass(FestivalCheckinCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().memberId()).isEqualTo(1L);
        assertThat(captor.getValue().festivalId()).isEqualTo(10L);
    }

    private Festival festivalAt(Long id, BigDecimal mapX, BigDecimal mapY) {
        FestivalSyncData data = new FestivalSyncData(
                "content-" + id,
                "15",
                "테스트 축제",
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
                Map.of("contentid", "content-" + id)
        );
        Festival festival = Festival.create(data, LocalDate.of(2026, 7, 18));
        ReflectionTestUtils.setField(festival, "id", id);
        return festival;
    }
}
