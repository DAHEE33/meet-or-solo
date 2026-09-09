package com.survey.meetorsolo.domain.member.dto;

import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberSanctionReason;
import java.time.OffsetDateTime;

/**
 * 제재된 회원에게 노출하는 사유·기간 안내.
 *
 * <p><b>여기에 담지 않는 것</b>
 * <ul>
 *   <li>{@code suspendedAt}(제재 시작 시각) — 제재 시점이 신고 시점을 좁히는 단서가 된다.</li>
 *   <li>{@code admin_actions.reason}(관리자 자유 입력 note) — 신고 건수·신고자를 적을 수 있다.</li>
 *   <li>신고 건수, 신고자 수, 관련 신고 id 등 신고자를 추정할 수 있는 모든 값.</li>
 * </ul>
 *
 * <p>{@code docs/19} 4.8과 5장의 신고자 보호 원칙을 따른다.
 *
 * <p><b>탈퇴 재가입 제한도 이 record로 전달한다</b>({@code docs/19} 4.4). 제재는 아니지만
 * 로그인 자체가 막히고 안내를 보여줘야 하는 상황이 같아서, 로그인 화면이 안내를 읽는 경로를
 * {@code GET /api/auth/sanction-notice} 하나로 유지하기 위해 함께 담는다. 경로를 새로 만들면
 * OAuth callback이 {@code 302}라 body가 없어 4.8이 고친 "소셜 로그인에 실패했습니다" 버그가
 * 재발한다.
 *
 * @param status            {@code SUSPENDED}, {@code BANNED} 또는 {@code WITHDRAWN}
 * @param suspendedUntil    정지 종료 시각. {@code BANNED}와 {@code WITHDRAWN}은 {@code null}
 * @param reasonCode        사용자 노출용 사유 code
 * @param reasonMessage     사용자 노출용 사유 문구
 * @param contactEmail      고객센터 이메일. 설정되지 않았으면 {@code null}
 * @param rejoinAvailableAt 재가입 가능 시각. 탈퇴 재가입 제한일 때만 값이 있다.
 *                          영구 거부이거나 제재 안내이면 {@code null}
 */
public record MemberSanctionNotice(
        String status,
        OffsetDateTime suspendedUntil,
        String reasonCode,
        String reasonMessage,
        String contactEmail,
        OffsetDateTime rejoinAvailableAt
) {

    /** 탈퇴 후 쿨오프가 남아 재가입이 막힌 경우. */
    public static final String REASON_CODE_REJOIN_COOLDOWN = "WITHDRAWN_REJOIN_COOLDOWN";

    /** 관리자 강제 탈퇴로 재가입이 영구 거부된 경우. */
    public static final String REASON_CODE_REJOIN_BLOCKED = "WITHDRAWN_REJOIN_BLOCKED";

    public static MemberSanctionNotice of(Member member, String contactEmail) {
        MemberSanctionReason reason = MemberSanctionReason.from(member.getSanctionReasonCode());
        return new MemberSanctionNotice(
                member.getStatus(),
                Member.STATUS_SUSPENDED.equals(member.getStatus()) ? member.getSuspendedUntil() : null,
                reason.name(),
                reason.getUserMessage(),
                normalizeContactEmail(contactEmail),
                null
        );
    }

    /**
     * 탈퇴 후 재가입이 막혔음을 알리는 안내.
     *
     * <p>사유 문구를 {@code MemberSanctionReason}에 넣지 않는다. 그 enum은 신고자 보호 전수
     * 검사({@code MemberSanctionReasonTest})의 대상이고, 탈퇴는 제재 사유가 아니다.
     *
     * @param rejoinAvailableAt 재가입 가능 시각. 영구 거부이면 {@code null}
     */
    public static MemberSanctionNotice forWithdrawn(
            OffsetDateTime rejoinAvailableAt, String contactEmail) {
        boolean permanent = rejoinAvailableAt == null;
        return new MemberSanctionNotice(
                Member.STATUS_WITHDRAWN,
                null,
                permanent ? REASON_CODE_REJOIN_BLOCKED : REASON_CODE_REJOIN_COOLDOWN,
                permanent
                        ? "탈퇴 처리된 계정이라 다시 가입할 수 없어요."
                        : "탈퇴한 계정이에요. 아래 시각이 지나면 다시 가입할 수 있어요.",
                normalizeContactEmail(contactEmail),
                rejoinAvailableAt
        );
    }

    private static String normalizeContactEmail(String contactEmail) {
        return (contactEmail == null || contactEmail.isBlank()) ? null : contactEmail;
    }
}
