package com.survey.meetorsolo.domain.festival.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.festival.dto.FestivalListItemResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalListResponse;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.service.FestivalQueryService;
import com.survey.meetorsolo.global.config.SecurityConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FestivalController.class)
@Import(SecurityConfig.class)
class FestivalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FestivalQueryService festivalQueryService;

    private FestivalListResponse listResponse;

    @BeforeEach
    void setUp() {
        listResponse = new FestivalListResponse(
                List.of(new FestivalListItemResponse(
                        1L,
                        "100",
                        "테스트 축제",
                        "강원특별자치도 테스트시",
                        "51",
                        "110",
                        LocalDate.of(2026, 7, 20),
                        LocalDate.of(2026, 7, 22),
                        FestivalStatus.ACTIVE,
                        "https://example.com/origin.jpg",
                        "https://example.com/thumbnail.jpg"
                )),
                0,
                20,
                1,
                1,
                false
        );
    }

    @Test
    void 축제_목록을_공통_응답_형식으로_반환한다() throws Exception {
        when(festivalQueryService.getActiveFestivals(0, 20)).thenReturn(listResponse);

        mockMvc.perform(get("/api/festivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].contentId").value("100"))
                .andExpect(jsonPath("$.data.items[0].thumbnailUrl")
                        .value("https://example.com/thumbnail.jpg"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        verify(festivalQueryService).getActiveFestivals(0, 20);
    }

    @Test
    void 페이지_크기가_100을_초과하면_validation_오류를_반환한다() throws Exception {
        mockMvc.perform(get("/api/festivals").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
