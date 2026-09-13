package com.survey.meetorsolo.domain.festival.history.dto;

import java.time.OffsetDateTime;

/**
 * 체크인 기록 한 건이다.
 *
 * <p>{@code status}는 서버가 판정해서 내린다. DB에는 {@code EXPIRED}가 실제로 기록되지 않고
 * {@code ACTIVE}/{@code CANCELLED}만 쓰이며 만료는 {@code expires_at} 경과로만 표현된다.
 * 화면이 이것을 다시 계산하면 {@code CheckinValidityPolicy}(유효 1시간)와 판정이 갈라진다.
 *
 * <p>원본 위경도는 저장 자체를 하지 않으므로 응답에도 없다. 축제 좌표와의 거리
 * ({@code distanceMeters})만 나간다.
 */
public record CheckinHistoryItemResponse(
        long checkinId,
        long festivalId,
        String festivalTitle,
        String festivalAddress,
        int distanceMeters,
        String status,
        OffsetDateTime checkedInAt,
        OffsetDateTime expiresAt
) {
}
