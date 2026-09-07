package com.survey.meetorsolo.domain.matching.history.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 매칭 기록 한 건이다.
 *
 * <p>{@code reportable}과 {@code reportableUntil}은 서버가 판정한다. 화면이 날짜를 직접
 * 계산하면 접수 API의 기간 정책과 어긋나 "신고 가능"으로 보이는 항목이 거절될 수 있다.
 */
public record MatchHistoryItemResponse(
        long groupId,
        String status,
        String festivalTitle,
        String festivalAddress,
        String meetingPlaceName,
        int confirmedMemberCount,
        OffsetDateTime endedAt,
        OffsetDateTime reportableUntil,
        boolean reportable,
        List<MatchHistoryMemberResponse> members
) {
}
