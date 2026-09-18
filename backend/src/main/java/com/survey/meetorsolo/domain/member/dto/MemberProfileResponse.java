package com.survey.meetorsolo.domain.member.dto;

import java.math.BigDecimal;
import java.util.List;

public record MemberProfileResponse(
        Long memberId,
        String nickname,
        String email,
        String intro,
        String profileImageUrl,
        String gender,
        String ageRange,
        String status,
        /**
         * 본인의 매너온도(docs/19 4.9).
         *
         * <p>본인 것만 내려준다. 다른 회원의 온도는 어디에도 노출하지 않는다 — 낮은 온도는
         * "신고를 받은 적이 있다"는 사실을 그대로 드러내고, 같은 만남에 있던 사람이 보면
         * 누가 신고했는지 좁힐 수 있다.
         *
         * <p>penalty_score는 담지 않는다. 노쇼 누적으로 쿨타임을 거는 내부 운영 값이고
         * 회원에게 보여줄 지표가 아니다.
         */
        BigDecimal mannerTemperature,
        /** 제재 중일 때만 채워진다. 화면이 활동 UI를 미리 막는 데 쓴다. */
        MemberSanctionNotice sanction,
        List<TravelStyleResponse> travelStyles
) {
}
