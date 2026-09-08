package com.survey.meetorsolo.domain.member.entity;

/**
 * 사용자에게 노출할 제재 사유와 문구.
 *
 * <p>문구는 이 enum에만 존재한다. 관리자 자유 입력 note({@code admin_actions.reason})는
 * 신고자 수·시점을 추정할 수 있는 내용이 들어갈 수 있어 사용자 응답에 실리지 않는다.
 *
 * <p><b>신고자 보호 제약</b>: 문구에 신고 건수·신고 시점·신고자 수를 암시하는 표현을 쓰지
 * 않는다. "신고 3건 누적"처럼 적으면 사용자가 자신을 신고한 사람의 범위를 좁힐 수 있다.
 * {@code docs/05_MATCHING_POLICY.md}의 신고 정책과 {@code docs/19} 5장을 따른다.
 * 이 제약은 {@code MemberSanctionReasonTest}가 문구 전수 검사로 못 박는다.
 *
 * <p>값 목록은 {@code AdminMemberActionReasonCode}와 이름이 1:1로 같아야 한다.
 * DB에서는 {@code chk_members_sanction_reason_code}가 같은 목록을 강제한다.
 */
public enum MemberSanctionReason {

    COMMUNITY_GUIDELINE("커뮤니티 이용 규칙 위반"),
    HARASSMENT("다른 이용자에 대한 부적절한 언행"),
    NO_SHOW_ABUSE("반복적인 약속 불이행"),
    FRAUD_OR_SCAM("사기 또는 금전 요구"),
    SAFETY_RISK("다른 이용자의 안전을 위협하는 행위"),
    ADMIN_CORRECTION("운영 정책에 따른 조치"),
    OTHER("운영 정책에 따른 조치");

    private final String userMessage;

    MemberSanctionReason(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }

    /**
     * 저장된 code를 사용자 노출용 사유로 바꾼다.
     *
     * <p>알 수 없는 code나 {@code null}은 {@link #OTHER}로 떨어뜨린다. 제재 안내 화면이
     * 사유 하나 때문에 실패하는 것보다 포괄 문구를 보여주는 편이 낫다.
     */
    public static MemberSanctionReason from(String code) {
        if (code == null || code.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(code);
        } catch (IllegalArgumentException exception) {
            return OTHER;
        }
    }
}
