package com.survey.meetorsolo.domain.festival.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.festival.dto.*;
import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.festival.service.FestivalMeetingPointAdminService;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminFestivalMeetingPointController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminFestivalMeetingPointControllerTest {
    private static final String BASE = "/api/admin/festivals/10/meeting-points";

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean FestivalMeetingPointAdminService service;

    @Test
    void access_token_cookie가_없으면_401이다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 일반_회원은_403이다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("user-token")).thenReturn(1L);
        when(service.list(1L, 10L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get(BASE).cookie(cookie("user-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void ADMIN은_목록을_200으로_조회한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("admin-token")).thenReturn(2L);
        when(service.list(2L, 10L)).thenReturn(List.of(response()));

        mockMvc.perform(get(BASE).cookie(cookie("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("테스트 장소"))
                .andExpect(jsonPath("$.data[0].status").value("INACTIVE"));
    }

    @Test
    void ADMIN은_유효한_후보를_201로_등록한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("admin-token")).thenReturn(2L);
        when(service.create(eq(2L), eq(10L), any())).thenReturn(response());

        mockMvc.perform(post(BASE).cookie(cookie("admin-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kakaoPlaceId").value("kakao-id"));
    }

    @Test
    void 빈_장소명은_400이다() throws Exception {
        authenticatedAdmin();
        mockMvc.perform(post(BASE).cookie(cookie("admin-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("테스트 장소", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 범위를_벗어난_좌표는_400이다() throws Exception {
        authenticatedAdmin();
        mockMvc.perform(post(BASE).cookie(cookie("admin-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("128.1", "181")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 다른_축제_소유의_meeting_point는_404다() throws Exception {
        authenticatedAdmin();
        when(service.update(eq(2L), eq(10L), eq(99L), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(put(BASE + "/99").cookie(cookie("admin-token"))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private void authenticatedAdmin() {
        when(jwtProvider.getMemberIdFromAccessToken("admin-token")).thenReturn(2L);
    }

    private jakarta.servlet.http.Cookie cookie(String value) {
        return new jakarta.servlet.http.Cookie("access_token", value);
    }

    private FestivalMeetingPointResponse response() {
        return new FestivalMeetingPointResponse(20L, 10L, "kakao-id", "테스트 장소",
                "강원 테스트로 1", new BigDecimal("128.1"), new BigDecimal("37.1"),
                FestivalMeetingPointStatus.INACTIVE, 1, null, null);
    }

    private String validBody() {
        return """
                {"kakaoPlaceId":"kakao-id","name":"테스트 장소","address":"강원 테스트로 1",
                 "longitude":128.1,"latitude":37.1,"assignmentOrder":1}
                """;
    }
}
