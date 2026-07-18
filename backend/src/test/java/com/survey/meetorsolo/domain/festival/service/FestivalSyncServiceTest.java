package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.domain.festival.config.FestivalSyncProperties;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.external.tourapi.client.TourApiClient;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FestivalSyncServiceTest {

    @Mock
    private TourApiClient tourApiClient;

    @Mock
    private FestivalSyncWriter writer;

    @Mock
    private FestivalSyncRetryWaiter retryWaiter;

    private FestivalSyncService service;

    @BeforeEach
    void setUp() {
        FestivalSyncProperties properties = new FestivalSyncProperties(
                true,
                Duration.ofSeconds(10),
                Duration.ofHours(6),
                2,
                10,
                30,
                365,
                "51",
                "EV",
                "EV01",
                3,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10)
        );
        service = new FestivalSyncService(
                tourApiClient,
                properties,
                new FestivalSyncMapper(new ObjectMapper()),
                writer,
                retryWaiter
        );
    }

    @Test
    void 모든_페이지를_받은_뒤_한_번만_DB에_반영한다() {
        when(tourApiClient.searchFestivals(any(SearchFestivalRequest.class)))
                .thenAnswer(invocation -> {
                    SearchFestivalRequest request = invocation.getArgument(0);
                    if (request.pageNo() == 1) {
                        return new TourApiPage<>(
                                2,
                                1,
                                3,
                                List.of(festivalItem("100", "첫 번째"), festivalItem("200", "두 번째"))
                        );
                    }
                    return new TourApiPage<>(
                            2,
                            2,
                            3,
                            List.of(festivalItem("300", "세 번째"))
                    );
                });
        when(writer.upsert(any(), any(LocalDate.class)))
                .thenReturn(new FestivalSyncWriteResult(3, 0, 3, 1, true));

        FestivalSyncResult result = service.synchronizeFestivals();

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.synchronizedCount()).isEqualTo(3);
        assertThat(result.insertedCount()).isEqualTo(3);
        assertThat(result.synchronizedImageCount()).isEqualTo(3);
        assertThat(result.endedCount()).isEqualTo(1);
        assertThat(result.initialLoad()).isTrue();

        ArgumentCaptor<SearchFestivalRequest> requestCaptor =
                ArgumentCaptor.forClass(SearchFestivalRequest.class);
        verify(tourApiClient, org.mockito.Mockito.times(2))
                .searchFestivals(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(SearchFestivalRequest::pageNo)
                .containsExactly(1, 2);
        assertThat(requestCaptor.getAllValues().get(0).regionCode()).isEqualTo("51");
        assertThat(requestCaptor.getAllValues().get(0).classificationSystem2()).isEqualTo("EV01");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<FestivalSyncData>> dataCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(writer).upsert(dataCaptor.capture(), any(LocalDate.class));
        assertThat(dataCaptor.getValue()).hasSize(3);
    }

    @Test
    void 중간_페이지가_실패하면_DB를_변경하지_않는다() {
        when(tourApiClient.searchFestivals(any(SearchFestivalRequest.class)))
                .thenAnswer(invocation -> {
                    SearchFestivalRequest request = invocation.getArgument(0);
                    if (request.pageNo() == 1) {
                        return new TourApiPage<>(
                                2,
                                1,
                                3,
                                List.of(festivalItem("100", "첫 번째"), festivalItem("200", "두 번째"))
                        );
                    }
                    throw new TourApiClientException(TourApiErrorType.NETWORK);
                });

        assertThatThrownBy(service::synchronizeFestivals)
                .isInstanceOf(TourApiClientException.class);
        verify(tourApiClient, times(4)).searchFestivals(any(SearchFestivalRequest.class));
        verify(retryWaiter).waitFor(Duration.ofSeconds(1));
        verify(retryWaiter).waitFor(Duration.ofSeconds(2));
        verify(writer, never()).upsert(any(), any(LocalDate.class));
    }

    @Test
    void 네트워크_오류가_일시적이면_대기_후_재시도한다() {
        AtomicInteger attempts = new AtomicInteger();
        when(tourApiClient.searchFestivals(any(SearchFestivalRequest.class)))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new TourApiClientException(TourApiErrorType.NETWORK);
                    }
                    return new TourApiPage<>(1, 1, 1, List.of(festivalItem("100", "축제")));
                });
        when(writer.upsert(any(), any(LocalDate.class)))
                .thenReturn(new FestivalSyncWriteResult(1, 0, 1, 0, true));

        FestivalSyncResult result = service.synchronizeFestivals();

        assertThat(result.synchronizedCount()).isEqualTo(1);
        verify(tourApiClient, times(2)).searchFestivals(any(SearchFestivalRequest.class));
        verify(retryWaiter).waitFor(Duration.ofSeconds(1));
    }

    @Test
    void HTTP_503은_최대_시도_횟수까지만_재시도한다() {
        when(tourApiClient.searchFestivals(any(SearchFestivalRequest.class)))
                .thenThrow(TourApiClientException.forHttpError(
                        TourApiErrorType.HTTP,
                        503,
                        null
                ));

        assertThatThrownBy(service::synchronizeFestivals)
                .isInstanceOfSatisfying(TourApiClientException.class, exception ->
                        assertThat(exception.getHttpStatus()).isEqualTo(503));

        verify(tourApiClient, times(3)).searchFestivals(any(SearchFestivalRequest.class));
        verify(retryWaiter).waitFor(Duration.ofSeconds(1));
        verify(retryWaiter).waitFor(Duration.ofSeconds(2));
        verify(writer, never()).upsert(any(), any(LocalDate.class));
    }

    @Test
    void HTTP_429는_대기_후_재시도한다() {
        when(tourApiClient.searchFestivals(any(SearchFestivalRequest.class)))
                .thenThrow(TourApiClientException.forHttpError(
                        TourApiErrorType.RATE_LIMIT,
                        429,
                        null
                ))
                .thenReturn(new TourApiPage<>(1, 1, 1, List.of(festivalItem("100", "축제"))));
        when(writer.upsert(any(), any(LocalDate.class)))
                .thenReturn(new FestivalSyncWriteResult(1, 0, 1, 0, true));

        FestivalSyncResult result = service.synchronizeFestivals();

        assertThat(result.synchronizedCount()).isEqualTo(1);
        verify(tourApiClient, times(2)).searchFestivals(any(SearchFestivalRequest.class));
        verify(retryWaiter).waitFor(Duration.ofSeconds(1));
    }

    @Test
    void HTTP_400은_재시도하지_않는다() {
        when(tourApiClient.searchFestivals(any(SearchFestivalRequest.class)))
                .thenThrow(TourApiClientException.forHttpError(
                        TourApiErrorType.HTTP,
                        400,
                        null
                ));

        assertThatThrownBy(service::synchronizeFestivals)
                .isInstanceOf(TourApiClientException.class);

        verify(tourApiClient).searchFestivals(any(SearchFestivalRequest.class));
        verify(retryWaiter, never()).waitFor(any(Duration.class));
        verify(writer, never()).upsert(any(), any(LocalDate.class));
    }

    @Test
    void 중복과_유효하지_않은_항목은_제외하고_저장한다() {
        when(tourApiClient.searchFestivals(any(SearchFestivalRequest.class)))
                .thenReturn(new TourApiPage<>(
                        3,
                        1,
                        3,
                        List.of(
                                festivalItem("100", "기존 제목"),
                                festivalItem("100", "최신 제목"),
                                festivalItem(null, "잘못된 항목")
                        )
                ));
        when(writer.upsert(any(), any(LocalDate.class)))
                .thenReturn(new FestivalSyncWriteResult(1, 0, 1, 0, true));

        FestivalSyncResult result = service.synchronizeFestivals();

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.synchronizedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<FestivalSyncData>> dataCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(writer).upsert(dataCaptor.capture(), any(LocalDate.class));
        assertThat(dataCaptor.getValue())
                .singleElement()
                .extracting(FestivalSyncData::title)
                .isEqualTo("최신 제목");
    }

    private SearchFestivalItem festivalItem(String contentId, String title) {
        return new SearchFestivalItem(
                "강원특별자치도 테스트시",
                null,
                null,
                contentId,
                "15",
                null,
                "20260720",
                "20260722",
                null,
                null,
                null,
                "128.1",
                "37.1",
                null,
                null,
                null,
                title,
                "51",
                "110",
                "EV",
                "EV01",
                null,
                null,
                null
        );
    }
}
