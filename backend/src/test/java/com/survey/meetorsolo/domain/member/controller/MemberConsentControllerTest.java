package com.survey.meetorsolo.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.member.dto.MemberConsentResponse;
import com.survey.meetorsolo.domain.member.dto.MemberConsentsResponse;
import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import com.survey.meetorsolo.domain.member.service.MemberConsentService;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberConsentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class MemberConsentControllerTest {

    private static final String PATH = "/api/members/me/consents";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberConsentService consentService;

    @MockitoBean
    private JwtProvider jwtProvider;

    private Cookie accessToken() {
        when(jwtProvider.getMemberIdFromAccessToken("token")).thenReturn(1L);
        return new Cookie("access_token", "token");
    }

    @Test
    void 동의_상태를_조회하면_기록이_없는_유형도_항목으로_내려간다() throws Exception {
        when(consentService.getAiConsents(1L)).thenReturn(new MemberConsentsResponse(List.of(
                new MemberConsentResponse("AI_PROCESSING", true, "1.0", OffsetDateTime.now(), null),
                new MemberConsentResponse("OVERSEAS_TRANSFER", false, "1.0", null, null)
        )));

        mockMvc.perform(get(PATH).cookie(accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.consents.length()").value(2))
                .andExpect(jsonPath("$.data.consents[0].consentType").value("AI_PROCESSING"))
                .andExpect(jsonPath("$.data.consents[0].agreed").value(true))
                .andExpect(jsonPath("$.data.consents[1].consentType").value("OVERSEAS_TRANSFER"))
                .andExpect(jsonPath("$.data.consents[1].agreed").value(false));
    }

    @Test
    void 인증_쿠키가_없으면_조회를_거절한다() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(consentService, never()).getAiConsents(any());
    }

    @Test
    void 동의를_기록한다() throws Exception {
        when(consentService.agree(1L, MemberConsentType.AI_PROCESSING)).thenReturn(
                new MemberConsentResponse("AI_PROCESSING", true, "1.0", OffsetDateTime.now(), null));

        mockMvc.perform(post(PATH)
                        .cookie(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"AI_PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreed").value(true))
                .andExpect(jsonPath("$.data.version").value("1.0"));

        verify(consentService).agree(1L, MemberConsentType.AI_PROCESSING);
    }

    @Test
    void 인증_쿠키가_없으면_동의_기록을_거절한다() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"AI_PROCESSING\"}"))
                .andExpect(status().isUnauthorized());

        verify(consentService, never()).agree(any(), eq(MemberConsentType.AI_PROCESSING));
    }

    @Test
    void 알_수_없는_동의_유형은_400으로_거절한다() throws Exception {
        mockMvc.perform(post(PATH)
                        .cookie(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 아직_화면이_없는_동의_유형은_400으로_거절한다() throws Exception {
        mockMvc.perform(post(PATH)
                        .cookie(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"MARKETING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 빈_동의_유형은_400으로_거절한다() throws Exception {
        mockMvc.perform(post(PATH)
                        .cookie(accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 동의를_철회한다() throws Exception {
        when(consentService.revoke(1L, MemberConsentType.OVERSEAS_TRANSFER)).thenReturn(
                new MemberConsentResponse(
                        "OVERSEAS_TRANSFER", false, "1.0", null, OffsetDateTime.now()));

        mockMvc.perform(delete(PATH + "/OVERSEAS_TRANSFER").cookie(accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreed").value(false))
                .andExpect(jsonPath("$.data.revokedAt").isNotEmpty());

        verify(consentService).revoke(1L, MemberConsentType.OVERSEAS_TRANSFER);
    }

    @Test
    void 인증_쿠키가_없으면_철회를_거절한다() throws Exception {
        mockMvc.perform(delete(PATH + "/AI_PROCESSING"))
                .andExpect(status().isUnauthorized());

        verify(consentService, never()).revoke(any(), eq(MemberConsentType.AI_PROCESSING));
    }
}
