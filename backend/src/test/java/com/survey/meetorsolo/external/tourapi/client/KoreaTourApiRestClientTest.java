package com.survey.meetorsolo.external.tourapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.survey.meetorsolo.external.tourapi.config.TourApiProperties;
import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiArrange;
import com.survey.meetorsolo.external.tourapi.exception.TourApiClientException;
import com.survey.meetorsolo.external.tourapi.exception.TourApiErrorType;
import com.survey.meetorsolo.external.tourapi.log.TourApiCallLogRecorder;
import com.survey.meetorsolo.external.tourapi.support.TourApiResponseParser;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KoreaTourApiRestClientTest {

    private final TourApiCallLogRecorder callLogRecorder = mock(TourApiCallLogRecorder.class);

    @Test
    void searchFestival2_성공_응답을_매핑한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaTourApiRestClient client = client(builder.build(), "test-key");
        server.expect(requestTo("https://apis.data.go.kr/B551011/KorService2/searchFestival2"
                        + "?serviceKey=test-key&numOfRows=10&pageNo=1&MobileOS=ETC"
                        + "&MobileApp=SoloIn&_type=json&arrange=C&eventStartDate=20260701"
                        + "&eventEndDate=20260731&lDongRegnCd=51&lclsSystm1=EV&lclsSystm2=EV01"))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        var page = client.searchFestivals(gangwonFestivalRequest());

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).contentId()).isEqualTo("3310483");
        assertThat(page.items().get(0).contentTypeId()).isEqualTo("15");
        assertThat(page.items().get(0).title()).isEqualTo("테스트 축제");
        assertThat(page.items().get(0).classificationSystem2()).isEqualTo("EV01");
        verify(callLogRecorder).recordSuccess(
                eq("searchFestival2"),
                eq("start=2026-07-01;end=2026-07-31;region=51;class=EV/EV01;page=1;rows=10"),
                eq(200),
                anyInt(),
                eq(1),
                any()
        );
        server.verify();
    }

    @Test
    void 인코딩된_서비스키를_이중_인코딩하지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaTourApiRestClient client = client(builder.build(), "abc%2Bdef%3D");
        server.expect(requestTo("https://apis.data.go.kr/B551011/KorService2/searchFestival2"
                        + "?serviceKey=abc%2Bdef%3D&numOfRows=10&pageNo=1&MobileOS=ETC"
                        + "&MobileApp=SoloIn&_type=json&arrange=C&eventStartDate=20260701"
                        + "&eventEndDate=20260731&lDongRegnCd=51&lclsSystm1=EV&lclsSystm2=EV01"))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        assertThat(client.searchFestivals(gangwonFestivalRequest()).items()).isEmpty();
        server.verify();
    }

    @Test
    void items가_빈_문자열이면_정상_빈_목록으로_처리한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaTourApiRestClient client = client(builder.build(), "test-key");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("searchFestival2")))
                .andRespond(withSuccess(emptyResponse(), MediaType.APPLICATION_JSON));

        var page = client.searchFestivals(gangwonFestivalRequest());

        assertThat(page.totalCount()).isZero();
        assertThat(page.items()).isEmpty();
        server.verify();
    }

    @Test
    void HTTP_200_XML_인증_오류를_구분한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaTourApiRestClient client = client(builder.build(), "test-key");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("searchFestival2")))
                .andRespond(withSuccess("""
                        <OpenAPI_ServiceResponse>
                          <cmmMsgHeader>
                            <errMsg>SERVICE ERROR</errMsg>
                            <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                            <returnReasonCode>30</returnReasonCode>
                          </cmmMsgHeader>
                        </OpenAPI_ServiceResponse>
                        """, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.searchFestivals(gangwonFestivalRequest()))
                .isInstanceOfSatisfying(TourApiClientException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(TourApiErrorType.AUTHORIZATION);
                    assertThat(exception.getRemoteCode()).isEqualTo("30");
                    assertThat(exception.getMessage())
                            .isEqualTo(TourApiErrorType.AUTHORIZATION.getDefaultMessage());
                });
        verify(callLogRecorder).recordFailure(
                eq("searchFestival2"),
                eq("start=2026-07-01;end=2026-07-31;region=51;class=EV/EV01;page=1;rows=10"),
                eq(200),
                anyInt(),
                any(TourApiClientException.class),
                any()
        );
        server.verify();
    }

    @Test
    void 분류_계층과_날짜_범위를_검증한다() {
        assertThatThrownBy(() -> new SearchFestivalRequest(
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 7, 1),
                null,
                1,
                10,
                TourApiArrange.MODIFIED,
                "51",
                null,
                "EV",
                "EV01",
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SearchFestivalRequest(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                1,
                10,
                TourApiArrange.MODIFIED,
                null,
                "110",
                "EV",
                "EV01",
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detailCommon2_소개글을_단일_객체_응답에서_조회한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaTourApiRestClient client = client(builder.build(), "test-key");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("detailCommon2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000", "resultMsg": "OK"},
                            "body": {
                              "items": {
                                "item": {
                                  "contentid": "3310483",
                                  "contenttypeid": "15",
                                  "title": "테스트 축제",
                                  "overview": "축제 소개글입니다."
                                }
                              },
                              "numOfRows": 1,
                              "pageNo": 1,
                              "totalCount": 1
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.fetchDetailCommon(
                new com.survey.meetorsolo.external.tourapi.dto.FestivalDetailApiRequest("3310483", "15")
        );

        assertThat(result).isPresent();
        assertThat(result.get().overview()).isEqualTo("축제 소개글입니다.");
        server.verify();
    }

    @Test
    void detailIntro2_이용정보_필드를_조회한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaTourApiRestClient client = client(builder.build(), "test-key");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("detailIntro2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000", "resultMsg": "OK"},
                            "body": {
                              "items": {
                                "item": {
                                  "contentid": "3310483",
                                  "sponsor1": "테스트시청",
                                  "sponsor1tel": "033-000-0000"
                                }
                              },
                              "numOfRows": 1,
                              "pageNo": 1,
                              "totalCount": 1
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.fetchDetailIntroFestival(
                new com.survey.meetorsolo.external.tourapi.dto.FestivalDetailApiRequest("3310483", "15")
        );

        assertThat(result).isPresent();
        assertThat(result.get().sponsor1()).isEqualTo("테스트시청");
        assertThat(result.get().sponsor1Tel()).isEqualTo("033-000-0000");
        server.verify();
    }

    @Test
    void detailInfo2_프로그램_반복_항목을_조회한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreaTourApiRestClient client = client(builder.build(), "test-key");
        server.expect(requestTo(org.hamcrest.Matchers.containsString("detailInfo2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "0000", "resultMsg": "OK"},
                            "body": {
                              "items": {
                                "item": [
                                  {"contentid": "3310483", "serialnum": "1", "infoname": "개막식", "infotext": "개막 공연"},
                                  {"contentid": "3310483", "serialnum": "2", "infoname": "폐막식", "infotext": "폐막 공연"}
                                ]
                              },
                              "numOfRows": 100,
                              "pageNo": 1,
                              "totalCount": 2
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.fetchDetailInfo(
                new com.survey.meetorsolo.external.tourapi.dto.FestivalDetailApiRequest("3310483", "15")
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).infoName()).isEqualTo("개막식");
        assertThat(result.get(1).infoName()).isEqualTo("폐막식");
        server.verify();
    }

    private KoreaTourApiRestClient client(RestClient restClient, String serviceKey) {
        TourApiProperties properties = new TourApiProperties(
                "https://apis.data.go.kr/B551011/KorService2",
                serviceKey,
                "ETC",
                "SoloIn",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        return new KoreaTourApiRestClient(
                restClient,
                properties,
                new TourApiResponseParser(new ObjectMapper()),
                callLogRecorder
        );
    }

    private SearchFestivalRequest gangwonFestivalRequest() {
        return new SearchFestivalRequest(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                1,
                10,
                TourApiArrange.MODIFIED,
                "51",
                null,
                "EV",
                "EV01",
                null
        );
    }

    private String successResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": {
                        "item": [{
                          "addr1": "강원특별자치도 테스트시",
                          "addr2": "테스트 장소",
                          "zipcode": "00000",
                          "contentid": "3310483",
                          "contenttypeid": "15",
                          "createdtime": "20260614132716",
                          "eventstartdate": "20260710",
                          "eventenddate": "20260712",
                          "firstimage": "https://example.com/origin.jpg",
                          "firstimage2": "https://example.com/thumbnail.jpg",
                          "cpyrhtDivCd": "Type3",
                          "mapx": "128.1234567890",
                          "mapy": "37.1234567890",
                          "mlevel": "6",
                          "modifiedtime": "20260701120000",
                          "tel": "033-000-0000",
                          "title": "테스트 축제",
                          "lDongRegnCd": "51",
                          "lDongSignguCd": "110",
                          "lclsSystm1": "EV",
                          "lclsSystm2": "EV01",
                          "lclsSystm3": "EV010100",
                          "progresstype": "진행중",
                          "festivaltype": "문화관광축제"
                        }]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
    }

    private String emptyResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": "",
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 0
                    }
                  }
                }
                """;
    }
}
