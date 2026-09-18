package com.survey.meetorsolo.domain.festival.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.festival.dto.CurrentCheckinResponse;
import com.survey.meetorsolo.domain.festival.service.FestivalCheckinService;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FestivalCheckinMeController.class)
@Import(SecurityConfig.class)
class FestivalCheckinMeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FestivalCheckinService festivalCheckinService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void 쿠키가_없으면_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/festivals/checkin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 활성_체크인이_없으면_data가_null인_200을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        when(festivalCheckinService.getCurrentCheckin(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/festivals/checkin/me")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 활성_체크인이_있으면_축제_정보를_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        when(festivalCheckinService.getCurrentCheckin(1L)).thenReturn(Optional.of(
                new CurrentCheckinResponse(
                        100L, 10L, "테스트 축제",
                        OffsetDateTime.parse("2026-07-26T10:00:00+09:00"),
                        OffsetDateTime.parse("2026-07-26T11:00:00+09:00")
                )
        ));

        mockMvc.perform(get("/api/festivals/checkin/me")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.festivalId").value(10))
                .andExpect(jsonPath("$.data.festivalName").value("테스트 축제"));
    }

    @Test
    void 쿠키가_없으면_취소는_401을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/festivals/checkin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 활성_체크인이_있으면_취소는_204를_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);

        mockMvc.perform(delete("/api/festivals/checkin/me")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isNoContent());
        verify(festivalCheckinService).cancelCurrentCheckin(eq(1L));
    }

    @Test
    void 활성_체크인이_없으면_취소는_service_예외를_그대로_전파한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(1L);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOT_FOUND, "활성 체크인이 없습니다."))
                .when(festivalCheckinService).cancelCurrentCheckin(1L);

        mockMvc.perform(delete("/api/festivals/checkin/me")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
