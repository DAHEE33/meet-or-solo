package com.survey.meetorsolo.domain.festival.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import com.survey.meetorsolo.domain.festival.service.FestivalCheckinService;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FestivalCheckinController.class)
@Import(SecurityConfig.class)
class FestivalCheckinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FestivalCheckinService festivalCheckinService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void 쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/festivals/1/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude": 37.0, "longitude": 128.0, "accuracyMeters": 10}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 위경도_범위를_벗어나면_400을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);

        mockMvc.perform(post("/api/festivals/1/checkin")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude": 999, "longitude": 128.0, "accuracyMeters": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 유효한_요청은_체크인_결과를_공통_응답으로_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        when(festivalCheckinService.checkIn(eq(1L), eq(1L), any(CheckInRequest.class))).thenReturn(
                new FestivalCheckinResponse(
                        100L, 1L, 120, FestivalCheckinStatus.ACTIVE,
                        OffsetDateTime.parse("2026-07-26T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-07-26T16:00:00+09:00")
                )
        );

        mockMvc.perform(post("/api/festivals/1/checkin")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude": 37.0, "longitude": 128.0, "accuracyMeters": 10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.distanceMeters").value(120))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        verify(festivalCheckinService).checkIn(eq(1L), eq(1L), any(CheckInRequest.class));
    }

    @Test
    void 체크인_범위_초과는_service_예외를_그대로_전파한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        when(festivalCheckinService.checkIn(eq(1L), eq(1L), any(CheckInRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.CHECKIN_OUT_OF_RANGE));

        mockMvc.perform(post("/api/festivals/1/checkin")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude": 37.0, "longitude": 128.0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CHECKIN_OUT_OF_RANGE"));
    }
}
