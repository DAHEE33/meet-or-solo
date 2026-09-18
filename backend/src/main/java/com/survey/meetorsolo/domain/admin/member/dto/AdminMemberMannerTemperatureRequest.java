package com.survey.meetorsolo.domain.admin.member.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 관리자 매너온도 수동 조정 요청({@code docs/19} 4.9).
 *
 * <p><b>{@code AdminMemberActionType}에 넣지 않고 별도 요청으로 둔다.</b> 제재 조치는 회원
 * 상태를 바꾸는 상태 전이이고 온도 조정은 상태를 전혀 바꾸지 않는다. 같은 요청 타입에 섞으면
 * {@code expectedStatus} 하나로 두 종류의 낙관적 잠금을 모두 표현해야 한다.
 *
 * @param targetTemperature 조정 후 목표값. 차감량이 아니다. 범위를 벗어나면 clamp하지 않고
 *                          거절한다.
 * @param expectedTemperature 관리자가 화면에서 본 현재 값. 다르면 {@code 409}로 거절한다.
 *                            제재의 {@code expectedStatus}와 같은 역할이며, 두 관리자가 같은
 *                            화면을 열어 두고 각자 조정해 나중 값이 앞 값을 덮는 것을 막는다.
 */
public record AdminMemberMannerTemperatureRequest(
        @NotNull
        @DecimalMin(value = "20.00", message = "매너온도는 20.00 이상이어야 합니다.")
        @DecimalMax(value = "42.00", message = "매너온도는 42.00 이하여야 합니다.")
        BigDecimal targetTemperature,
        @NotNull BigDecimal expectedTemperature,
        @NotNull AdminMemberActionReasonCode reasonCode,
        @Size(max = 500) String reasonNote
) {
}
