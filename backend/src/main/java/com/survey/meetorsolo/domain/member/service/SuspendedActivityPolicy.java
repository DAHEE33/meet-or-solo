package com.survey.meetorsolo.domain.member.service;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * 정지({@code SUSPENDED}) 회원에게 막는 활동 요청 목록.
 *
 * <p>정지는 조회를 막지 않는다. 여기 등재된 요청만 막는다({@code docs/19} 4.8).
 *
 * <p><b>목록 방식의 위험과 그 대비</b><br>
 * 새 활동 endpoint를 만들고 {@link #RESTRICTED}에 넣는 것을 잊으면 정지 회원이 그 기능을
 * 조용히 쓸 수 있다. 그래서 {@code SuspendedActivityPolicyCoverageTest}가 상태를 바꾸는
 * 모든 endpoint를 전수 조사해 {@link #RESTRICTED}나 {@link #ALLOWED}에 없으면 실패한다.
 * <b>새 endpoint를 추가하면 둘 중 하나에 반드시 분류해야 한다.</b>
 *
 * <p>{@code /api/auth/**}와 {@code /api/admin/**}은 이 판정 대상이 아니다. 전자는
 * {@code MemberAccessInterceptor} 제외 경로이고, 후자는 {@code AdminAuthorizationService}가
 * {@code requireAccessible}로 정지 회원을 이미 차단한다.
 */
@Component
public class SuspendedActivityPolicy {

    /**
     * 정지 중 막는 활동.
     *
     * <p>체크인이 매칭의 전제이고 매칭이 동행의 전제이므로, 셋을 함께 막아야 참여 경로가 닫힌다.
     * 댓글 작성과 좋아요는 다른 사용자에게 보이는 행위라 함께 막는다.
     */
    private static final List<Rule> RESTRICTED = List.of(
            Rule.of("POST", "/api/festivals/*/checkin"),
            Rule.of("POST", "/api/matching/pools"),
            Rule.of("POST", "/api/matching/proposals/*/responses"),
            Rule.of("POST", "/api/festivals/*/comments"),
            Rule.of("POST", "/api/spots/*/comments"),
            Rule.of("PUT", "/api/comments/*/like")
    );

    /**
     * 정지 중에도 허용하는 상태 변경 요청. 허용 근거를 여기 남긴다.
     *
     * <p>이 목록은 코드 동작에 쓰이지 않는다. 전수 조사 테스트가 "분류를 빠뜨린 endpoint"와
     * "의도적으로 허용한 endpoint"를 구분하는 근거로만 쓴다.
     */
    private static final List<Rule> ALLOWED = List.of(
            // 안전 기능. 정지는 신고·차단 권리를 박탈하는 조치가 아니다. 만남 종료 후 14일 안에
            // 정지되면 신고 경로가 사라지는 문제도 생긴다(MatchReportWindowPolicy).
            Rule.of("POST", "/api/match-groups/*/reports"),
            Rule.of("POST", "/api/match-groups/*/blocks"),
            Rule.of("DELETE", "/api/members/me/blocks/*"),
            // 이의제기 경로. 제재 사유를 다툴 수 없으면 제재가 일방적이 된다. 영구제한 회원은
            // requireBrowsable에 막혀 이 경로에 도달조차 못 하므로(고객센터 이메일 안내 유지),
            // 정지 회원에게도 막으면 인앱 이의제기 경로가 아예 사라진다(docs/29 2.1, 2.3).
            Rule.of("POST", "/api/members/me/inquiries"),
            Rule.of("POST", "/api/members/me/inquiries/*/messages"),
            // 개인정보 권리. 동의와 철회는 제재로 막을 수 없다.
            Rule.of("POST", "/api/members/me/consents"),
            Rule.of("DELETE", "/api/members/me/consents/*"),
            // 탈퇴도 개인정보 권리다. 정지 중이라고 계정 삭제를 막을 수 없다.
            // 잔여 정지 기간은 탈퇴로 사라지지 않고 재가입 시 이어진다(Member.rejoin).
            Rule.of("DELETE", "/api/members/me"),
            // 자기 정보 관리. 다른 사용자와의 상호작용이 아니다.
            Rule.of("PUT", "/api/members/me/profile"),
            Rule.of("POST", "/api/members/me/profile-image"),
            Rule.of("POST", "/api/members/me/preference-embedding"),
            Rule.of("DELETE", "/api/members/me/preference-embedding"),
            // 개인 저장. 다른 사용자에게 보이지 않는다.
            Rule.of("PUT", "/api/festivals/*/bookmark"),
            Rule.of("PUT", "/api/spots/*/bookmark"),
            // 정리 행위. 남겨둔 것을 거두는 요청은 막을 이유가 없다.
            Rule.of("DELETE", "/api/comments/*"),
            Rule.of("DELETE", "/api/festivals/checkin/me"),
            Rule.of("PUT", "/api/matching/pools/me/current/cancellation"),
            Rule.of("PUT", "/api/matching/groups/me/current/cancellation"),
            // 진행 중 만남에서만 쓰이는 요청. 활성 매칭이 있는 회원은 애초에 정지되지 않는다
            // (AdminMemberService의 ADMIN_MEMBER_ACTIVE_MATCH_CONFLICT).
            Rule.of("PUT", "/api/matching/groups/me/current/arrival"),
            Rule.of("PUT", "/api/matching/groups/me/current/arrival-time")
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 정지 회원에게 막아야 하는 요청인지 판정한다. */
    public boolean isRestricted(String method, String path) {
        return matches(RESTRICTED, method, path);
    }

    /** 의도적으로 허용한 상태 변경 요청인지 판정한다. 전수 조사 테스트가 쓴다. */
    public boolean isExplicitlyAllowed(String method, String path) {
        return matches(ALLOWED, method, path);
    }

    private boolean matches(List<Rule> rules, String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        return rules.stream().anyMatch(rule ->
                rule.method().equalsIgnoreCase(method) && matcher.match(rule.pattern(), path));
    }

    private record Rule(String method, String pattern) {
        static Rule of(String method, String pattern) {
            return new Rule(method, pattern);
        }
    }
}
