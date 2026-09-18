package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.festival.config.FestivalDetailInfoProperties;
import com.survey.meetorsolo.domain.festival.dto.FestivalDetailInfo;
import com.survey.meetorsolo.external.tourapi.client.TourApiClient;
import com.survey.meetorsolo.external.tourapi.dto.DetailCommonItem;
import com.survey.meetorsolo.external.tourapi.dto.DetailInfoItem;
import com.survey.meetorsolo.external.tourapi.dto.DetailIntroFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.FestivalDetailApiRequest;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FestivalDetailInfoServiceTest {

    @Mock
    private TourApiClient tourApiClient;

    private FestivalDetailInfoService service(Duration cacheTtl) {
        return new FestivalDetailInfoService(tourApiClient, new FestivalDetailInfoProperties(cacheTtl));
    }

    @Test
    void 세_API_응답을_합쳐서_intro_infoItems_programs를_구성한다() {
        when(tourApiClient.fetchDetailCommon(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.of(new DetailCommonItem("100", "15", "테스트 축제", "소개글<br>둘째 줄")));
        when(tourApiClient.fetchDetailIntroFestival(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.of(new DetailIntroFestivalItem(
                        "100", null, null, null, null,
                        "테스트시청", "033-000-0000", null, null, null, null, null
                )));
        when(tourApiClient.fetchDetailInfo(any(FestivalDetailApiRequest.class)))
                .thenReturn(List.of(new DetailInfoItem("100", "1", "개막식", "개막 공연")));

        FestivalDetailInfo result = service(Duration.ofHours(1)).getDetailInfo("100", "15");

        assertThat(result.intro()).isEqualTo("소개글\n둘째 줄");
        assertThat(result.infoItems()).contains(
                new com.survey.meetorsolo.domain.festival.dto.FestivalInfoItem("주최", "테스트시청 · 033-000-0000")
        );
        assertThat(result.programs()).containsExactly(
                new com.survey.meetorsolo.domain.festival.dto.FestivalProgramItem("개막식", "개막 공연", "")
        );
    }

    @Test
    void TTL_이내_재조회는_캐시를_사용하고_API를_다시_호출하지_않는다() {
        when(tourApiClient.fetchDetailCommon(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.of(new DetailCommonItem("100", "15", "테스트 축제", "소개글")));
        when(tourApiClient.fetchDetailIntroFestival(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.empty());
        when(tourApiClient.fetchDetailInfo(any(FestivalDetailApiRequest.class)))
                .thenReturn(List.of());
        FestivalDetailInfoService service = service(Duration.ofHours(1));

        service.getDetailInfo("100", "15");
        service.getDetailInfo("100", "15");
        service.getDetailInfo("100", "15");

        verify(tourApiClient, times(1)).fetchDetailCommon(any(FestivalDetailApiRequest.class));
    }

    @Test
    void 콘텐츠_ID가_다르면_캐시를_공유하지_않는다() {
        when(tourApiClient.fetchDetailCommon(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.of(new DetailCommonItem("100", "15", "테스트 축제", "소개글")));
        when(tourApiClient.fetchDetailIntroFestival(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.empty());
        when(tourApiClient.fetchDetailInfo(any(FestivalDetailApiRequest.class)))
                .thenReturn(List.of());
        FestivalDetailInfoService service = service(Duration.ofHours(1));

        service.getDetailInfo("100", "15");
        service.getDetailInfo("200", "15");

        verify(tourApiClient, times(2)).fetchDetailCommon(any(FestivalDetailApiRequest.class));
    }

    @Test
    void 소개글_조회가_실패해도_이용정보와_프로그램은_반환한다() {
        when(tourApiClient.fetchDetailCommon(any(FestivalDetailApiRequest.class)))
                .thenThrow(new TourApiClientException(TourApiErrorType.NETWORK));
        when(tourApiClient.fetchDetailIntroFestival(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.of(new DetailIntroFestivalItem(
                        "100", null, null, null, null,
                        "테스트시청", null, null, null, null, null, null
                )));
        when(tourApiClient.fetchDetailInfo(any(FestivalDetailApiRequest.class)))
                .thenReturn(List.of());

        FestivalDetailInfo result = service(Duration.ofHours(1)).getDetailInfo("100", "15");

        assertThat(result.intro()).isEmpty();
        assertThat(result.infoItems()).isNotEmpty();
    }

    @Test
    void 전부_실패하면_빈_결과를_캐싱하지_않고_다음_호출에서_다시_시도한다() {
        when(tourApiClient.fetchDetailCommon(any(FestivalDetailApiRequest.class)))
                .thenThrow(new TourApiClientException(TourApiErrorType.NETWORK));
        when(tourApiClient.fetchDetailIntroFestival(any(FestivalDetailApiRequest.class)))
                .thenThrow(new TourApiClientException(TourApiErrorType.NETWORK));
        when(tourApiClient.fetchDetailInfo(any(FestivalDetailApiRequest.class)))
                .thenThrow(new TourApiClientException(TourApiErrorType.NETWORK));
        FestivalDetailInfoService service = service(Duration.ofHours(1));

        FestivalDetailInfo first = service.getDetailInfo("100", "15");
        service.getDetailInfo("100", "15");

        assertThat(first.isEmpty()).isTrue();
        verify(tourApiClient, times(2)).fetchDetailCommon(any(FestivalDetailApiRequest.class));
    }

    @Test
    void TTL_만료_후에는_다시_API를_호출한다() throws InterruptedException {
        when(tourApiClient.fetchDetailCommon(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.of(new DetailCommonItem("100", "15", "테스트 축제", "소개글")));
        when(tourApiClient.fetchDetailIntroFestival(any(FestivalDetailApiRequest.class)))
                .thenReturn(Optional.empty());
        when(tourApiClient.fetchDetailInfo(any(FestivalDetailApiRequest.class)))
                .thenReturn(List.of());
        FestivalDetailInfoService service = service(Duration.ofMillis(50));

        service.getDetailInfo("100", "15");
        Thread.sleep(120);
        service.getDetailInfo("100", "15");

        verify(tourApiClient, times(2)).fetchDetailCommon(any(FestivalDetailApiRequest.class));
    }
}
