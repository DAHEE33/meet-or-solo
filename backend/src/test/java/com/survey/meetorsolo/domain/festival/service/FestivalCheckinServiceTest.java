package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.config.FestivalCheckinProperties;
import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckin;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FestivalCheckinServiceTest {

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private FestivalCheckinRepository festivalCheckinRepository;

    private FestivalCheckinService service() {
        return new FestivalCheckinService(
                festivalRepository,
                festivalCheckinRepository,
                new FestivalCheckinProperties(Duration.ofHours(6), 100)
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
