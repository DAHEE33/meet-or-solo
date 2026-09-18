package com.survey.meetorsolo.domain.admin.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 강제 탈퇴 요청({@code docs/19} 4.4).
 *
 * <p><b>{@code AdminMemberActionType}에 넣지 않고 별도 요청으로 둔다.</b> 관리자 {@code BAN}은
 * 계정이 남아 되돌릴 수 있고 강제 탈퇴는 익명화라 되돌릴 수 없다. 같은 상태 전이나 API로
 * 처리하지 않는다는 {@code docs/19} 4.4 원칙을 지킨다.
 *
 * @param blockRejoin 재가입을 영구 거부할지. 기본값은 {@code true}(제재성 강제 탈퇴).
 *                    로그인이 막힌 회원이 고객센터로 요청한 탈퇴 대행이면 {@code false}로 보낸다.
 *                    관리자가 의식적으로 고르게 하기 위해 서버가 사유 code로 추측하지 않는다.
 */
public record AdminMemberForcedWithdrawalRequest(
        @NotNull AdminMemberActionReasonCode reasonCode,
        @Size(max = 500) String reasonNote,
        @NotNull AdminMemberStatus expectedStatus,
        Boolean blockRejoin
) {

    /** 값이 없으면 재가입을 차단한다. 되돌릴 수 없는 조치의 기본값은 보수적으로 둔다. */
    public boolean blocksRejoin() {
        return blockRejoin == null || blockRejoin;
    }
}
