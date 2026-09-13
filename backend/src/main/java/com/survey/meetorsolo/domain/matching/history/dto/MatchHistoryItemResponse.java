package com.survey.meetorsolo.domain.matching.history.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 매칭 기록 한 건이다.
 *
 * <p>{@code reportable}과 {@code reportableUntil}은 서버가 판정한다. 화면이 날짜를 직접
 * 계산하면 접수 API의 기간 정책과 어긋나 "신고 가능"으로 보이는 항목이 거절될 수 있다.
 *
 * <p>{@code meetingHeld}는 실제로 만남 장소에 도착한 사람이 있었는지다. {@code reportable}이
 * {@code false}일 때 <b>기간이 지나서인지 만남이 없어서인지</b>를 화면이 구분해 안내하려면
 * 필요하다. 두 경우의 안내 문구가 다르다({@code docs/19} 4.11.1).
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
        boolean meetingHeld,
        boolean reportable,
        List<MatchHistoryMemberResponse> members
) {
}
