package com.survey.meetorsolo.domain.admin.member.service;

import com.survey.meetorsolo.domain.admin.member.dto.AdminMemberStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 회원 목록 필터.
 *
 * <p>{@code testAccount}는 "테스트 계정만"을 뜻하고 {@code null}이면 조건을 걸지 않는다.
 * "테스트 계정 제외"는 두지 않는다 — 쓰임이 없고, 조건이 늘어나면 cursor fingerprint가
 * 그만큼 갈라진다.
 *
 * <p>역할(USER/ADMIN) 조건은 없다. 관리자 진입을 로컬 계정 하나로 한정하면서 역할로 걸러 볼
 * 이유가 사라졌다. 목록에서 관리자를 빼는 것은 이제 filter가 아니라
 * {@code AdminMemberRepository.findPage}의 고정 조건이 맡는다.
 */
public record AdminMemberFilter(
        String query, AdminMemberStatus status, Boolean testAccount) {

    public String fingerprint() {
        // fingerprint에 새 조건을 빠뜨리면, 필터를 바꿔도 이전 필터의 cursor가 그대로
        // 통과해 페이지 경계가 어긋난다.
        String canonical = value(query) + "|" + value(status) + "|" + value(testAccount);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }
}
