package com.survey.meetorsolo.domain.matching.event;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 매칭 상태가 바뀌었음을 알린다. WebSocket 발송과 알림함 저장이 이 이벤트 하나를 구독한다.
 *
 * @param actorMemberId 그 변화를 만든 회원. 스케줄러가 만든 변화(제안 생성·시간 초과·노쇼·
 *                      만남 종료)는 행위자가 없어 {@code null}이다. 알림함이 이 값을 보고
 *                      <b>행위자 본인에게는 남기지 않는다</b> — 내가 도착을 눌렀는데
 *                      "상대가 도착했어요"가 내 목록에 쌓이던 문제다(`docs/31` 5절).
 */
public record MatchingStateChangedEvent(
        List<Long> memberIds,
        String reason,
        OffsetDateTime occurredAt,
        Long actorMemberId
) {

    public MatchingStateChangedEvent {
        memberIds = List.copyOf(memberIds);
    }

    /** 행위자가 없는 변화(스케줄러·배치)를 위한 생성자. */
    public MatchingStateChangedEvent(
            List<Long> memberIds, String reason, OffsetDateTime occurredAt) {
        this(memberIds, reason, occurredAt, null);
    }
}
