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
 * @param status         {@code SUSPENDED} 또는 {@code BANNED}
 * @param suspendedUntil 정지 종료 시각. {@code BANNED}는 기간이 없어 {@code null}
 * @param reasonCode     사용자 노출용 사유 code
 * @param reasonMessage  사용자 노출용 사유 문구
 * @param contactEmail   고객센터 이메일. 설정되지 않았으면 {@code null}
 */
public record MemberSanctionNotice(
        String status,
        OffsetDateTime suspendedUntil,
        String reasonCode,
        String reasonMessage,
        String contactEmail
) {

    public static MemberSanctionNotice of(Member member, String contactEmail) {
        MemberSanctionReason reason = MemberSanctionReason.from(member.getSanctionReasonCode());
        return new MemberSanctionNotice(
                member.getStatus(),
                Member.STATUS_SUSPENDED.equals(member.getStatus()) ? member.getSuspendedUntil() : null,
                reason.name(),
                reason.getUserMessage(),
                (contactEmail == null || contactEmail.isBlank()) ? null : contactEmail
        );
    }
}
