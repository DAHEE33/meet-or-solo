package com.survey.meetorsolo.domain.admin.member.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 테스트 계정 지정·해제 요청.
 *
 * <p>제재(`AdminMemberActionRequest`)·강제 탈퇴·매너온도와 타입을 분리한다. 이 조치는 회원
 * 상태를 바꾸지 않고 사유 code도 받지 않는다. 사유를 강제하면 "테스트하려고"밖에 쓸 말이
 * 없어 형식만 남는다.
 *
 * <p>토글이 아니라 목표 값을 받는다. 토글은 두 관리자가 동시에 누르면 결과가 뒤집히지만
 * 목표 값은 몇 번을 보내도 같은 상태가 된다.
 */
public record AdminMemberTestAccountRequest(
        @NotNull Boolean enabled,
        String reasonNote
) {
}
