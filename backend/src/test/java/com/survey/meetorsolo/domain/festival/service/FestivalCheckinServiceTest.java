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
    private ApplicationEventPublisher eventPublisher;

    private FestivalCheckinService service() {
        return service(false);
    }

    private FestivalCheckinService service(boolean bypassRadiusCheck) {
        return new FestivalCheckinService(
                festivalRepository,
                festivalCheckinRepository,
                new FestivalCheckinProperties(100, bypassRadiusCheck),
                eventPublisher
        );
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
