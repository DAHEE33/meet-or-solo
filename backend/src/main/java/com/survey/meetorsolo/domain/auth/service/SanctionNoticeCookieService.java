package com.survey.meetorsolo.domain.auth.service;

import com.survey.meetorsolo.domain.auth.jwt.JwtProvider;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 제재 안내 조회 cookie를 만들고 읽는다.
 *
 * <p>제재로 막히는 경로는 세 곳이다. OAuth callback(302), {@code MemberAccessInterceptor}(403),
 * {@code AuthService.refresh}(403). 이 중 302에는 응답 body가 없어 403 body만으로는 로그인
 * 화면에 사유·기간을 전달할 수 없다. 그래서 세 경로 모두 같은 cookie를 내려주고 로그인 화면이
 * {@code GET /api/auth/sanction-notice} 하나로 안내를 읽게 한다. 문구와 판정이 한 곳에만 남는다.
 *
 * <p>사유·기간을 query parameter로 넘기지 않는 이유는 URL·nginx access log·브라우저 history에
 * 제재 정보가 남고 누구나 URL을 위조해 안내 화면을 띄울 수 있기 때문이다.
 */
@Service
public class SanctionNoticeCookieService {

    public static final String COOKIE_NAME = "sanction_notice";

    /** 조회 endpoint 밖으로 cookie가 전송되지 않도록 path를 좁힌다. */
    private static final String COOKIE_PATH = "/api/auth/sanction-notice";

    private final JwtProvider jwtProvider;
    private final boolean secureCookies;

    public SanctionNoticeCookieService(
            JwtProvider jwtProvider,
            @Value("${app.auth.cookie-secure}") boolean secureCookies
    ) {
        this.jwtProvider = jwtProvider;
        this.secureCookies = secureCookies;
    }

    public ResponseCookie issue(long memberId) {
        return base(jwtProvider.createSanctionNoticeToken(memberId))
                .maxAge(Duration.ofSeconds(jwtProvider.getSanctionNoticeTokenExpiresInSeconds()))
                .build();
    }

    /** 안내를 읽은 뒤 cookie를 즉시 지운다. 남겨둘 이유가 없다. */
    public ResponseCookie expire() {
        return base("").maxAge(Duration.ZERO).build();
    }

    /**
     * cookie의 token에서 회원 id를 읽는다.
     * 서명·type·만료가 맞지 않으면 {@code JwtProvider}가 {@code UNAUTHORIZED}를 던진다.
     */
    public long readMemberId(String token) {
        return jwtProvider.getMemberIdFromSanctionNoticeToken(token);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path(COOKIE_PATH);
    }
}
