package com.survey.meetorsolo.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import com.survey.meetorsolo.domain.auth.service.SanctionNoticeCookieService;
import com.survey.meetorsolo.domain.member.dto.MemberSanctionNotice;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberSanctionReason;
import com.survey.meetorsolo.domain.member.service.MemberSanctionException;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.response.ApiResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 제재 403 응답의 wire 형태 검증.
 *
 * <p>사용자가 사유·기간을 보는 경로가 여기와 {@code /api/auth/sanction-notice} 두 곳이므로
 * body에 무엇이 담기고 무엇이 담기지 않는지를 직렬화 결과로 확인한다.
 */
class GlobalExceptionHandlerSanctionTest {

    private static final OffsetDateTime SUSPENDED_UNTIL = OffsetDateTime.parse("2026-09-15T10:00:00+09:00");

    private final JwtProvider jwtProvider = new JwtProvider(
            new ObjectMapper(), "sanction-handler-test-secret-long-enough", 30, 20160);
    private final SanctionNoticeCookieService cookies =
            new SanctionNoticeCookieService(jwtProvider, true);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(provider(cookies));
    /** 앱과 같은 직렬화 설정을 쓴다. application.yml의 write-dates-as-timestamps=false와 맞춘다. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void 제재_403은_사유와_종료시각을_body에_담는다() throws Exception {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMemberSanctionException(suspension());

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        String json = objectMapper.writeValueAsString(response.getBody());
        assertThat(json)
                .contains("\"code\":\"MEMBER_SUSPENDED\"")
                .contains("\"reasonCode\":\"HARASSMENT\"")
                .contains("\"status\":\"SUSPENDED\"")
                .contains("2026-09-15T10:00");
    }

    /**
     * 신고자 보호. 제재 시작 시각은 신고 시점을 좁히는 단서라 응답에 담지 않는다.
     * 관리자 자유 입력 note도 body에 실리지 않는다.
     */
    @Test
    void 제재_403은_제재_시작시각과_관리자_note를_담지_않는다() throws Exception {
        String json = objectMapper.writeValueAsString(
                handler.handleMemberSanctionException(suspension()).getBody());

        assertThat(json)
                .doesNotContain("suspendedAt")
                .doesNotContain("신고")
                .doesNotContain("누적")
                .doesNotContain("reporter")
                .doesNotContain("reportId");
    }

    @Test
    void 제재_403은_안내_조회용_cookie를_함께_내린다() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMemberSanctionException(suspension());

        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("sanction_notice=")
                .contains("Path=/api/auth/sanction-notice")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");
    }

    @Test
    void 회원_id가_없으면_cookie_없이_안내만_내린다() {
        MemberSanctionException exception = new MemberSanctionException(
                ErrorCode.MEMBER_BANNED, null, notice(Member.STATUS_BANNED, null));

        ResponseEntity<ApiResponse<Void>> response = handler.handleMemberSanctionException(exception);

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(response.getBody().error().sanction()).isNotNull();
    }

    @Test
    void 제재가_아닌_실패_응답에는_안내가_붙지_않는다() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThat(response.getBody().error().sanction()).isNull();
    }

    private static MemberSanctionException suspension() {
        return new MemberSanctionException(
                ErrorCode.MEMBER_SUSPENDED, 7L, notice(Member.STATUS_SUSPENDED, SUSPENDED_UNTIL));
    }

    private static MemberSanctionNotice notice(String status, OffsetDateTime suspendedUntil) {
        return new MemberSanctionNotice(
                status,
                suspendedUntil,
                MemberSanctionReason.HARASSMENT.name(),
                MemberSanctionReason.HARASSMENT.getUserMessage(),
                null,
                null);
    }

    /** {@code GlobalExceptionHandler}가 쓰는 lazy 주입을 테스트에서 대신한다. */
    private static ObjectProvider<SanctionNoticeCookieService> provider(SanctionNoticeCookieService value) {
        return new ObjectProvider<>() {
            @Override
            public SanctionNoticeCookieService getObject() {
                return value;
            }

            @Override
            public SanctionNoticeCookieService getObject(Object... args) {
                return value;
            }

            @Override
            public SanctionNoticeCookieService getIfAvailable() {
                return value;
            }

            @Override
            public SanctionNoticeCookieService getIfUnique() {
                return value;
            }
        };
    }
}
