package com.survey.meetorsolo.domain.tourplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.domain.tourplace.config.TourPlaceSyncProperties;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceSyncData;
import com.survey.meetorsolo.external.tourapi.client.TourApiClient;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceItem;
import com.survey.meetorsolo.external.tourapi.dto.SearchTourPlaceRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiPage;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourPlaceSyncServiceTest {

    @Mock
    private TourApiClient tourApiClient;

    @Mock
    private TourPlaceSyncWriter writer;

    @Mock
    private TourPlaceSyncRetryWaiter retryWaiter;

    private TourPlaceSyncService service(List<String> contentTypeIds) {
        return service(contentTypeIds, 100);
    }

    private TourPlaceSyncService service(List<String> contentTypeIds, int batchSize) {
        TourPlaceSyncProperties properties = new TourPlaceSyncProperties(
                true,
                Duration.ofSeconds(20),
                Duration.ofHours(12),
                2,
                10,
                "51",
                contentTypeIds,
                batchSize,
                3,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10)
        );
        return new TourPlaceSyncService(
                tourApiClient,
                properties,
                new TourPlaceSyncMapper(new ObjectMapper()),
                writer,
                retryWaiter
        );
    }

    @Test
    void 모든_페이지를_받은_뒤_INACTIVE_정리를_한_번만_실행한다() {
        TourPlaceSyncService service = service(List.of("12"));
        when(tourApiClient.searchTourPlaces(any(SearchTourPlaceRequest.class)))
                .thenAnswer(invocation -> {
                    SearchTourPlaceRequest request = invocation.getArgument(0);
                    if (request.pageNo() == 1) {
                        return new TourApiPage<>(
                                2,
                                1,
                                3,
                                List.of(placeItem("100", "첫 번째"), placeItem("200", "두 번째"))
                        );
                    }
                    return new TourApiPage<>(2, 2, 3, List.of(placeItem("300", "세 번째")));
                });
        when(writer.upsertBatch(any())).thenReturn(new TourPlaceSyncWriteResult(3, 0, 0));
        when(writer.markMissingInactive(any(TourPlaceSyncScope.class))).thenReturn(2);

        TourPlaceSyncResult result = service.synchronizeTourPlaces();

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.synchronizedCount()).isEqualTo(3);
        assertThat(result.insertedCount()).isEqualTo(3);
        assertThat(result.inactiveCount()).isEqualTo(2);

        ArgumentCaptor<SearchTourPlaceRequest> requestCaptor =
                ArgumentCaptor.forClass(SearchTourPlaceRequest.class);
        verify(tourApiClient, times(2)).searchTourPlaces(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(SearchTourPlaceRequest::pageNo)
                .containsExactly(1, 2);
        assertThat(requestCaptor.getAllValues().get(0).contentTypeId()).isEqualTo("12");
        assertThat(requestCaptor.getAllValues().get(0).regionCode()).isEqualTo("51");

        // batchSize(100) > 전체 건수(3)이므로 모든 페이지 수집이 끝난 뒤 마지막 잔여분 한 번만 커밋된다.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TourPlaceSyncData>> dataCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(writer, times(1)).upsertBatch(dataCaptor.capture());
        assertThat(dataCaptor.getValue()).hasSize(3);

        ArgumentCaptor<TourPlaceSyncScope> scopeCaptor = ArgumentCaptor.forClass(TourPlaceSyncScope.class);
        verify(writer).markMissingInactive(scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().observedContentIds())
                .containsExactlyInAnyOrder("100", "200", "300");
        assertThat(scopeCaptor.getValue().contentTypeId()).isEqualTo("12");
    }

    @Test
    void 설정된_모든_콘텐츠_타입을_순회하며_동기화한다() {
        TourPlaceSyncService service = service(List.of("12", "39"));
        when(tourApiClient.searchTourPlaces(any(SearchTourPlaceRequest.class)))
                .thenAnswer(invocation -> {
                    SearchTourPlaceRequest request = invocation.getArgument(0);
                    return new TourApiPage<>(
                            10,
                            1,
                            1,
                            List.of(placeItem("100", "테스트", request.contentTypeId()))
                    );
                });
        when(writer.upsertBatch(any())).thenReturn(new TourPlaceSyncWriteResult(1, 0, 0));

        TourPlaceSyncResult result = service.synchronizeTourPlaces();

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.synchronizedCount()).isEqualTo(2);
        assertThat(result.insertedCount()).isEqualTo(2);

        ArgumentCaptor<TourPlaceSyncScope> scopeCaptor = ArgumentCaptor.forClass(TourPlaceSyncScope.class);
        verify(writer, times(2)).markMissingInactive(scopeCaptor.capture());
        assertThat(scopeCaptor.getAllValues())
                .extracting(TourPlaceSyncScope::contentTypeId)
                .containsExactly("12", "39");
    }

    @Test
    void 배치_크기에_도달하면_페이지_수집_도중에도_즉시_커밋한다() {
        TourPlaceSyncService service = service(List.of("12"), 2);
        when(tourApiClient.searchTourPlaces(any(SearchTourPlaceRequest.class)))
                .thenAnswer(invocation -> {
                    SearchTourPlaceRequest request = invocation.getArgument(0);
                    if (request.pageNo() == 1) {
                        return new TourApiPage<>(
                                2,
                                1,
                                4,
                                List.of(placeItem("100", "첫 번째"), placeItem("200", "두 번째"))
                        );
                    }
                    return new TourApiPage<>(
                            2,
                            2,
                            4,
                            List.of(placeItem("300", "세 번째"), placeItem("400", "네 번째"))
                    );
                });
        when(writer.upsertBatch(any())).thenReturn(new TourPlaceSyncWriteResult(2, 0, 0));

        TourPlaceSyncResult result = service.synchronizeTourPlaces();

        assertThat(result.fetchedCount()).isEqualTo(4);
        assertThat(result.synchronizedCount()).isEqualTo(4);
        assertThat(result.insertedCount()).isEqualTo(4);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TourPlaceSyncData>> dataCaptor =
                ArgumentCaptor.forClass(Collection.class);
        // 배치 크기(2)만큼 페이지 1건 수집 직후 한 번, 페이지 2건 수집 직후(잔여분) 한 번 커밋된다.
        verify(writer, times(2)).upsertBatch(dataCaptor.capture());
        assertThat(dataCaptor.getAllValues().get(0))
                .extracting(TourPlaceSyncData::contentId)
                .containsExactly("100", "200");
        assertThat(dataCaptor.getAllValues().get(1))
                .extracting(TourPlaceSyncData::contentId)
                .containsExactly("300", "400");
        verify(writer, times(1)).markMissingInactive(any(TourPlaceSyncScope.class));
    }

    @Test
    void 타입_수집_중_실패해도_이미_커밋된_배치는_유지되고_INACTIVE_정리는_실행되지_않는다() {
        TourPlaceSyncService service = service(List.of("12"), 2);
        when(tourApiClient.searchTourPlaces(any(SearchTourPlaceRequest.class)))
                .thenAnswer(invocation -> {
                    SearchTourPlaceRequest request = invocation.getArgument(0);
                    if (request.pageNo() == 1) {
                        return new TourApiPage<>(
                                2,
                                1,
                                4,
                                List.of(placeItem("100", "첫 번째"), placeItem("200", "두 번째"))
                        );
                    }
                    throw new TourApiClientException(TourApiErrorType.NETWORK);
                });
        when(writer.upsertBatch(any())).thenReturn(new TourPlaceSyncWriteResult(2, 0, 0));

        assertThatThrownBy(service::synchronizeTourPlaces)
                .isInstanceOf(TourApiClientException.class);

        // 1페이지(2건)는 배치 크기에 도달해 이미 커밋됐고, 2페이지 수집 실패로 나머지는 반영되지 않는다.
        verify(writer, times(1)).upsertBatch(any());
        verify(writer, never()).markMissingInactive(any(TourPlaceSyncScope.class));
    }

    @Test
    void 중간_페이지가_실패하면_배치_크기에_못_미친_수집분은_반영하지_않는다() {
        TourPlaceSyncService service = service(List.of("12"));
        when(tourApiClient.searchTourPlaces(any(SearchTourPlaceRequest.class)))
                .thenAnswer(invocation -> {
                    SearchTourPlaceRequest request = invocation.getArgument(0);
                    if (request.pageNo() == 1) {
                        return new TourApiPage<>(
                                2,
                                1,
                                3,
                                List.of(placeItem("100", "첫 번째"), placeItem("200", "두 번째"))
                        );
                    }
                    throw new TourApiClientException(TourApiErrorType.NETWORK);
                });

        assertThatThrownBy(service::synchronizeTourPlaces)
                .isInstanceOf(TourApiClientException.class);
        verify(tourApiClient, times(4)).searchTourPlaces(any(SearchTourPlaceRequest.class));
        verify(retryWaiter).waitFor(Duration.ofSeconds(1));
        verify(retryWaiter).waitFor(Duration.ofSeconds(2));
        verify(writer, never()).upsertBatch(any());
        verify(writer, never()).markMissingInactive(any(TourPlaceSyncScope.class));
    }

    @Test
    void HTTP_503은_최대_시도_횟수까지만_재시도한다() {
        TourPlaceSyncService service = service(List.of("12"));
        when(tourApiClient.searchTourPlaces(any(SearchTourPlaceRequest.class)))
                .thenThrow(TourApiClientException.forHttpError(TourApiErrorType.HTTP, 503, null));

        assertThatThrownBy(service::synchronizeTourPlaces)
                .isInstanceOfSatisfying(TourApiClientException.class, exception ->
                        assertThat(exception.getHttpStatus()).isEqualTo(503));

        verify(tourApiClient, times(3)).searchTourPlaces(any(SearchTourPlaceRequest.class));
        verify(writer, never()).upsertBatch(any());
    }

    @Test
    void HTTP_400은_재시도하지_않는다() {
        TourPlaceSyncService service = service(List.of("12"));
        when(tourApiClient.searchTourPlaces(any(SearchTourPlaceRequest.class)))
                .thenThrow(TourApiClientException.forHttpError(TourApiErrorType.HTTP, 400, null));

        assertThatThrownBy(service::synchronizeTourPlaces)
                .isInstanceOf(TourApiClientException.class);

        verify(tourApiClient).searchTourPlaces(any(SearchTourPlaceRequest.class));
        verify(retryWaiter, never()).waitFor(any(Duration.class));
        verify(writer, never()).upsertBatch(any());
    }

    @Test
    void 중복과_유효하지_않은_항목은_제외하고_저장한다() {
        TourPlaceSyncService service = service(List.of("12"));
        when(tourApiClient.searchTourPlaces(any(SearchTourPlaceRequest.class)))
                .thenReturn(new TourApiPage<>(
                        3,
                        1,
                        3,
                        List.of(
                                placeItem("100", "기존 이름"),
                                placeItem("100", "최신 이름"),
                                placeItem(null, "잘못된 항목")
                        )
                ));
        when(writer.upsertBatch(any())).thenReturn(new TourPlaceSyncWriteResult(1, 0, 0));

        TourPlaceSyncResult result = service.synchronizeTourPlaces();

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.skippedCount()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TourPlaceSyncData>> dataCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(writer).upsertBatch(dataCaptor.capture());
        assertThat(dataCaptor.getValue())
                .singleElement()
                .extracting(TourPlaceSyncData::title)
                .isEqualTo("최신 이름");
    }

    private SearchTourPlaceItem placeItem(String contentId, String title) {
        return placeItem(contentId, title, "12");
    }

    private SearchTourPlaceItem placeItem(String contentId, String title, String contentTypeId) {
        return new SearchTourPlaceItem(
                "강원특별자치도 테스트시",
                null,
                null,
                contentId,
                contentTypeId,
                null,
                null,
                null,
                null,
                "128.1",
                "37.1",
                null,
                null,
                null,
                title,
                "A01",
                null,
                null,
                "51",
                "110"
        );
    }
}
