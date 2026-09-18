package com.survey.meetorsolo.domain.member.dto;

import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import java.time.OffsetDateTime;

/**
 * 동의 유형 하나의 현재 상태.
 *
 * <p>기록이 아예 없거나 철회된 경우에도 항목 자체는 내려보내고 `agreed = false`로 표시한다.
 * 화면이 "어떤 동의가 비어 있는가"를 알아야 체크박스를 그릴 수 있기 때문이다.
 */
public record MemberConsentResponse(
        String consentType,
        boolean agreed,
        String version,
        OffsetDateTime agreedAt,
        OffsetDateTime revokedAt
) {

    /** 기록이 없는 유형의 기본 상태. 현재 고지 문구 버전을 함께 알려준다. */
    public static MemberConsentResponse notAgreed(MemberConsentType type) {
        return new MemberConsentResponse(type.name(), false, type.currentVersion(), null, null);
    }
}
