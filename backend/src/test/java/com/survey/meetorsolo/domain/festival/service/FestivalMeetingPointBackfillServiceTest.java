package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPoint;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalMeetingPointRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FestivalMeetingPointBackfillServiceTest {

    private final FestivalRepository festivals = mock(FestivalRepository.class);
    private final FestivalMeetingPointRepository points = mock(FestivalMeetingPointRepository.class);
    private final FestivalMeetingPointBackfillService service =
            new FestivalMeetingPointBackfillService(festivals, points);

    @Test
    void 좌표가_없는_축제는_건너뛰고_저장하지_않는다() {
        Festival festival = festival(1L, "no-coords", "좌표 없는 축제", "강원 어딘가", null, null);
        when(festivals.findAllByStatusWithoutMeetingPoint(FestivalStatus.ACTIVE))
                .thenReturn(List.of(festival));

        int seeded = service.seedMissingDefaultPoints();

        assertThat(seeded).isEqualTo(0);
        verify(points, never()).save(any());
    }

    @Test
    void 장소가_0건인_ACTIVE_축제에_축제_좌표로_기본_장소를_ACTIVE로_생성한다() {
        Festival festival = festival(10L, "fixture-100", "강릉 단오제",
                "강원특별자치도 강릉시 단오장길 1", new BigDecimal("128.8961230000"), new BigDecimal("37.7524560000"));
        when(festivals.findAllByStatusWithoutMeetingPoint(FestivalStatus.ACTIVE))
                .thenReturn(List.of(festival));
        when(points.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int seeded = service.seedMissingDefaultPoints();

        assertThat(seeded).isEqualTo(1);
        ArgumentCaptor<FestivalMeetingPoint> captor = ArgumentCaptor.forClass(FestivalMeetingPoint.class);
        verify(points).save(captor.capture());
        FestivalMeetingPoint saved = captor.getValue();
        assertThat(saved.getFestivalId()).isEqualTo(10L);
        assertThat(saved.getKakaoPlaceId()).isEqualTo("AUTO-fixture-100");
        assertThat(saved.getName()).isEqualTo("강릉 단오제 (자동 등록 기본 위치)");
        assertThat(saved.getAddress()).isEqualTo("강원특별자치도 강릉시 단오장길 1");
        assertThat(saved.getMapX()).isEqualTo(new BigDecimal("128.8961230000"));
        assertThat(saved.getMapY()).isEqualTo(new BigDecimal("37.7524560000"));
        assertThat(saved.getStatus()).isEqualTo(FestivalMeetingPointStatus.ACTIVE);
        assertThat(saved.getAssignmentOrder()).isEqualTo(0);
    }

    @Test
    void 축제_주소가_없으면_placeholder_주소를_사용한다() {
        Festival festival = festival(11L, "fixture-101", "주소 없는 축제", null,
                new BigDecimal("128.1"), new BigDecimal("37.1"));
        when(festivals.findAllByStatusWithoutMeetingPoint(FestivalStatus.ACTIVE))
                .thenReturn(List.of(festival));
        when(points.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.seedMissingDefaultPoints();

        ArgumentCaptor<FestivalMeetingPoint> captor = ArgumentCaptor.forClass(FestivalMeetingPoint.class);
        verify(points).save(captor.capture());
        assertThat(captor.getValue().getAddress()).isEqualTo("주소 미확인 (관리자 확인 필요)");
    }

    @Test
    void 여러_대상_중_좌표_없는_축제만_건너뛰고_나머지는_생성한다() {
        Festival withoutCoords = festival(1L, "no-coords", "좌표 없는 축제", "강원 어딘가", null, null);
        Festival withCoords = festival(2L, "fixture-102", "좌표 있는 축제", "강원 다른곳",
                new BigDecimal("128.5"), new BigDecimal("37.5"));
        when(festivals.findAllByStatusWithoutMeetingPoint(FestivalStatus.ACTIVE))
                .thenReturn(List.of(withoutCoords, withCoords));
        when(points.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int seeded = service.seedMissingDefaultPoints();

        assertThat(seeded).isEqualTo(1);
        ArgumentCaptor<FestivalMeetingPoint> captor = ArgumentCaptor.forClass(FestivalMeetingPoint.class);
        verify(points).save(captor.capture());
        assertThat(captor.getValue().getFestivalId()).isEqualTo(2L);
    }

    private Festival festival(long id, String contentId, String title, String address,
            BigDecimal mapX, BigDecimal mapY) {
        Festival festival = mock(Festival.class);
        when(festival.getId()).thenReturn(id);
        when(festival.getContentId()).thenReturn(contentId);
        when(festival.getTitle()).thenReturn(title);
        when(festival.getAddress()).thenReturn(address);
        when(festival.getMapX()).thenReturn(mapX);
        when(festival.getMapY()).thenReturn(mapY);
        return festival;
    }
}
