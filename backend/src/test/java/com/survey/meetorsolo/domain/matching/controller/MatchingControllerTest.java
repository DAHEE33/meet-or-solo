package com.survey.meetorsolo.domain.matching.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolResponse;
import com.survey.meetorsolo.domain.matching.service.MatchPoolEntryService;
import com.survey.meetorsolo.domain.matching.service.MatchProposalActionService;
import com.survey.meetorsolo.domain.matching.service.MatchingQueryService;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchingController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class MatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private MatchPoolEntryService poolEntries;

    @MockitoBean
    private MatchingQueryService queries;

    @MockitoBean
    private MatchProposalActionService proposalActions;

    @Test
    void 인증_쿠키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/matching/pools/me/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 잘못된_access_token은_401을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/api/matching/pools/me/current")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "invalid-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void access_token에서_얻은_회원_ID로_pool을_생성한다() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T15:00:00+09:00");
        MatchPoolEntryRequest request = new MatchPoolEntryRequest(10L, 2, false, List.of());
        MatchPoolResponse response = new MatchPoolResponse(
                30L, 10L, 2, false, List.of(), "WAITING", now, now.plusSeconds(60));
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(poolEntries.enter(20L, request)).thenReturn(response);

        mockMvc.perform(post("/api/matching/pools")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "festivalId": 10,
                                  "preferredGroupSize": 2,
                                  "allowMinimumTwo": false,
                                  "tags": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.poolId").value(30))
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        verify(poolEntries).enter(20L, request);
    }

    @Test
    void 현재_pool이_없으면_200과_null_data를_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(queries.currentPool(20L)).thenReturn(null);

        mockMvc.perform(get("/api/matching/pools/me/current")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void active_proposal이_없으면_200과_null_data를_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(queries.activeProposal(20L)).thenReturn(null);

        mockMvc.perform(get("/api/matching/proposals/me/active")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 정의되지_않은_action은_400을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);

        mockMvc.perform(post("/api/matching/proposals/1/responses")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START_WITH_CURRENT_MEMBERS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 비어있지_않은_tags는_400을_반환한다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);

        mockMvc.perform(post("/api/matching/pools")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "festivalId": 10,
                                  "preferredGroupSize": 2,
                                  "allowMinimumTwo": false,
                                  "tags": ["PHOTO"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("tags"));
    }

    @Test
    void 기존_응답_변경_충돌은_409_공통_응답이다() throws Exception {
        when(jwtProvider.getMemberIdFromAccessToken("valid-token")).thenReturn(20L);
        when(proposalActions.respond(
                20L,
                1L,
                com.survey.meetorsolo.domain.matching.dto.MatchProposalActionRequest.Action.REJECT
        )).thenThrow(new BusinessException(ErrorCode.MATCHING_CONFLICT));

        mockMvc.perform(post("/api/matching/proposals/1/responses")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "valid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCHING_CONFLICT"));
    }
}
