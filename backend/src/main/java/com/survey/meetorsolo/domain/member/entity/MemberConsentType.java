package com.survey.meetorsolo.domain.member.entity;

import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * `member_consents.consent_type`에 저장되는 동의 유형.
 *
 * <p>DB CHECK 제약(`chk_member_consents_type`)과 값이 일치해야 한다. V2에서 4종으로 시작했고
 * V11에서 AI 관련 2종을 추가했다.
 *
 * <p>`currentVersion`은 화면에 노출한 고지 문구의 버전이다. 문구를 개정하면 이 값을 올려
 * 새 row가 쌓이도록 한다. 다만 현재 동의 여부 조회는 version을 보지 않으므로 값을 올려도
 * 기존 동의자에게 재동의를 강제하지는 않는다.
 */
public enum MemberConsentType {

    TERMS("1.0"),
    PRIVACY("1.0"),
    LOCATION("1.0"),
    MARKETING("1.0"),
    AI_PROCESSING("1.0"),
    OVERSEAS_TRANSFER("1.0");

    private final String currentVersion;

    MemberConsentType(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    /**
     * 취향 글을 외부 임베딩 API로 보내기 위해 필요한 동의.
     *
     * <p>AI 처리와 국외 이전은 법적 성격과 거부 선택이 다르므로 하나로 합치지 않는다.
     * 하나라도 없으면 외부 전송을 하지 않는다.
     */
    public static final List<MemberConsentType> AI_EMBEDDING_REQUIRED =
            List.of(AI_PROCESSING, OVERSEAS_TRANSFER);

    /** 현재 API로 기록·철회할 수 있는 유형. LOCATION, MARKETING은 아직 화면이 없다. */
    private static final Set<MemberConsentType> API_MANAGED =
            EnumSet.of(TERMS, PRIVACY, AI_PROCESSING, OVERSEAS_TRANSFER);

    public String currentVersion() {
        return currentVersion;
    }

    public boolean isApiManaged() {
        return API_MANAGED.contains(this);
    }

    /** API 요청 값을 enum으로 바꾼다. 알 수 없거나 아직 API로 다루지 않는 값은 400으로 거절한다. */
    public static MemberConsentType from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        MemberConsentType type;
        try {
            type = MemberConsentType.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!type.isApiManaged()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return type;
    }
}
