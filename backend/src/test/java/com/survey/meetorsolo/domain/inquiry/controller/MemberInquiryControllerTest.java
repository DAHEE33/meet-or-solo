package com.survey.meetorsolo.domain.inquiry.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryDetailResponse;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryUnreadCountResponse;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import com.survey.meetorsolo.domain.inquiry.service.InquiryService;
import com.survey.meetorsolo.global.config.SecurityConfig;
import com.survey.meetorsolo.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberInquiryController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class MemberInquiryControllerTest {

    private static final String TOKEN = "member-token";
    private static final long MEMBER_ID = 1L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 9, 9, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean InquiryService inquiries;

    private Cookie cookie() {
        when(jwtProvider.getMemberIdFromAccessToken(TOKEN)).thenReturn(MEMBER_ID);
        return new Cookie("access_token", TOKEN);
    }

    @Test
    void cookie가_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/members/me/inquiries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void unread_count는_상세_경로로_잘못_해석되지_않는다() throws Exception {
        // /unread-count와 /{inquiryId}가 같은 depth라 라우팅 모호성이 생길 수 있는 자리다.
        // 상세로 해석되면 "unread-count"를 long으로 파싱하다 400이 난다.
        when(inquiries.getUnreadAnswerCount(MEMBER_ID))
                .thenReturn(new InquiryUnreadCountResponse(2L));

        mockMvc.perform(get("/api/members/me/inquiries/unread-count").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        verify(inquiries, never()).getMyInquiry(anyLong(), anyLong());
    }

    @Test
    void 상세_경로는_숫자_id로_해석된다() throws Exception {
        when(inquiries.getMyInquiry(MEMBER_ID, 7L)).thenReturn(detail());

        mockMvc.perform(get("/api/members/me/inquiries/7").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inquiryId").value(7));
    }

    @Test
    void 등록은_201과_생성된_스레드를_반환한다() throws Exception {
        when(inquiries.create(MEMBER_ID, InquiryCategory.SANCTION_APPEAL, "제목", "본문"))
                .thenReturn(detail());

        mockMvc.perform(post("/api/members/me/inquiries")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"SANCTION_APPEAL","title":"제목","body":"본문"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inquiryId").value(7));
    }

    @Test
    void 등록_요청의_priority는_무시된다() throws Exception {
        // 긴급 지정은 관리자만 한다. 요청 record에 필드가 없어 값이 흘러들 자리가 없다
        // (docs/29 확정 5번).
        when(inquiries.create(MEMBER_ID, InquiryCategory.ETC, "제목", "본문")).thenReturn(detail());

        mockMvc.perform(post("/api/members/me/inquiries")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"ETC","title":"제목","body":"본문","priority":"URGENT"}
                                """))
                .andExpect(status().isCreated());

        verify(inquiries).create(MEMBER_ID, InquiryCategory.ETC, "제목", "본문");
    }

    @Test
    void 제목이_없으면_400이다() throws Exception {
        mockMvc.perform(post("/api/members/me/inquiries")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"ETC","title":"","body":"본문"}
                                """))
                .andExpect(status().isBadRequest());

        verify(inquiries, never()).create(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void size가_상한을_넘으면_400이다() throws Exception {
        mockMvc.perform(get("/api/members/me/inquiries?size=999").cookie(cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INQUIRY_INVALID_REQUEST"));
    }

    private static InquiryDetailResponse detail() {
        return new InquiryDetailResponse(
                7L, InquiryCategory.SANCTION_APPEAL, "제목", InquiryStatus.RECEIVED,
                List.of(), NOW, null);
    }
}
