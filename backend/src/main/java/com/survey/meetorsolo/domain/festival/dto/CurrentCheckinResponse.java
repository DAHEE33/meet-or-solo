package com.survey.meetorsolo.domain.festival.dto;

import java.time.OffsetDateTime;

/**
 * 인증 회원의 현재 유효한(ACTIVE, 만료 전) 체크인 1건. {@code /matching} 화면이 "체크인하기"
 * 대신 어느 축제에 체크인되어 있는지·언제 만료되는지를 보여주기 위해 사용한다.
 * festivalName은 축제가 이미 삭제·비공개 처리된 예외적인 경우 null일 수 있다.
 */
public record CurrentCheckinResponse(
        Long checkinId,
        Long festivalId,
        String festivalName,
        OffsetDateTime checkedInAt,
        OffsetDateTime expiresAt
) {
}
