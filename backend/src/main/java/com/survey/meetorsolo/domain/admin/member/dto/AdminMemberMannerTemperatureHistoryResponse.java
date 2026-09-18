package com.survey.meetorsolo.domain.admin.member.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 관리자 매너온도 조정 이력 한 건({@code docs/19} 4.9).
 *
 * <p>{@code AdminMemberActionHistoryResponse}(제재 이력)와 분리한다. 제재 이력의
 * {@code actionType}은 {@code AdminMemberActionType} enum이고, 여기에 온도 조정을 넣으면
 * 조치 <b>요청</b> enum에 요청할 수 없는 값이 섞인다.
 *
 * <p>이력을 화면에 보여주는 이유는 두 관리자가 같은 회원의 온도를 모르고 중복 조정하는 것을
 * 막기 위해서다. 값만 보여서는 그 값이 자동 하강의 결과인지 누가 손댄 결과인지 알 수 없다.
 */
public record AdminMemberMannerTemperatureHistoryResponse(
        long actionId,
        BigDecimal beforeTemperature,
        BigDecimal afterTemperature,
        AdminMemberActionReasonCode reasonCode,
        String reasonNote,
        OffsetDateTime createdAt
) {
}
