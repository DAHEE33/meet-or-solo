package com.survey.meetorsolo.domain.tourplace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceDetailResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListItemResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListResponse;
import com.survey.meetorsolo.domain.tourplace.dto.TourPlaceListSort;
import com.survey.meetorsolo.domain.tourplace.entity.TourPlaceStatus;
import com.survey.meetorsolo.domain.tourplace.service.TourPlaceQueryService;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TourPlaceController.class)
@Import(SecurityConfig.class)
class TourPlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TourPlaceQueryService tourPlaceQueryService;

    @Test
    void 관광지_목록을_공통_응답_형식으로_반환한다() throws Exception {
        TourPlaceListResponse listResponse = new TourPlaceListResponse(
                List.of(new TourPlaceListItemResponse(
                        1L,
                        "100",
                        "12",
                        "테스트 관광지",
                        "강원특별자치도 테스트시",
                        TourPlaceStatus.ACTIVE,
                        "https://example.com/image.jpg"
                )),
                0,
                20,
                1,
                1,
                false
        );
        when(tourPlaceQueryService.getVisiblePlaces(eq(0), eq(20), isNull(), isNull(), isNull(), eq(TourPlaceListSort.TITLE_ASC))).thenReturn(listResponse);

        mockMvc.perform(get("/api/spots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].title").value("테스트 관광지"));
        verify(tourPlaceQueryService).getVisiblePlaces(0, 20, null, null, null, TourPlaceListSort.TITLE_ASC);
    }

    @Test
    void keyword_파라미터를_그대로_전달한다() throws Exception {
        TourPlaceListResponse listResponse = new TourPlaceListResponse(List.of(), 0, 20, 0, 0, false);
        when(tourPlaceQueryService.getVisiblePlaces(eq(0), eq(20), isNull(), eq("맛집"), isNull(), eq(TourPlaceListSort.TITLE_ASC)))
                .thenReturn(listResponse);

        mockMvc.perform(get("/api/spots").param("keyword", "맛집"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(tourPlaceQueryService).getVisiblePlaces(0, 20, null, "맛집", null, TourPlaceListSort.TITLE_ASC);
    }

    @Test
    void 관광지_상세를_공통_응답_형식으로_반환한다() throws Exception {
        TourPlaceDetailResponse detail = new TourPlaceDetailResponse(
                1L, "100", "12", "테스트 관광지", "강원특별자치도 테스트시", "033-000-0000",
                null, null, TourPlaceStatus.ACTIVE, "https://example.com/image.jpg"
        );
        when(tourPlaceQueryService.getTourPlaceDetail(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/spots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("테스트 관광지"));
    }

    @Test
    void 존재하지_않는_관광지_상세_조회는_404를_반환한다() throws Exception {
        when(tourPlaceQueryService.getTourPlaceDetail(99L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "관광지를 찾을 수 없습니다."));

        mockMvc.perform(get("/api/spots/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 관광지_주변_축제를_거리순으로_반환한다() throws Exception {
        when(tourPlaceQueryService.getNearbyFestivals(eq(1L), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/spots/1/nearby-festivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(tourPlaceQueryService).getNearbyFestivals(1L, 5000, 10);
    }
}
