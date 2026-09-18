package com.survey.meetorsolo.domain.matching.service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 만남 자체의 길이와 완료 조건을 정의한다({@code docs/19} 4.11.2).
 *
 * <p><b>전원이 도착한 순간을 완료로 보지 않는다.</b> 그 시점은 만남의 끝이 아니라 시작이다.
 * 예전 구현은 마지막 사람이 도착 버튼을 누르면 곧바로 그룹을 {@code COMPLETED}로 닫고 매너온도
 * 보상까지 지급했는데, 사용자 입장에서는 이제 막 만난 상태에서 상태방이 사라졌다.
 *
 * <p>그래서 완료 판정을 <b>확정 시각 + {@link #MEETING_WINDOW}</b>로 옮겼다. 이 값이
 * {@link MatchCompletionLockPolicy#MATCH_VALIDITY}와 같은 것은 우연이 아니다. 재매칭 잠금은
 * "만남이 진행 중인 동안 다른 매칭을 잡지 못한다"는 뜻이므로 만남의 길이와 같아야 한다.
 * 두 값이 어긋나면 방은 닫혔는데 잠금만 남거나, 잠금은 풀렸는데 방이 살아 있는 구간이 생긴다.
 *
 * <p>도착 마감({@link MatchArrivalDeadlinePolicy#ARRIVAL_WINDOW} 30분)은 그대로 둔다. 그쪽은
 * "언제까지 와야 노쇼가 아닌가"이고 이쪽은 "만남이 언제 끝나는가"라 목적이 다르다.
 */
public final class MatchMeetingWindowPolicy {

    /**
     * 확정 시각부터 만남이 끝났다고 보는 시각까지의 길이.
     *
     * <p>{@link MatchCompletionLockPolicy#MATCH_VALIDITY}와 같은 값이어야 한다.
     */
    public static final Duration MEETING_WINDOW = Duration.ofHours(1);

    /**
     * 만남이 성립했다고 보는 최소 도착 인원.
     *
     * <p>{@code docs/05}의 최소 매칭 인원 2명과 같다. <b>혼자 도착한 것은 만남이 아니다.</b>
     * 예전 완료 판정은 {@code activeMembers.allMatch(ARRIVED)}였는데, 상대가 모두 이탈해
     * 활성 구성원이 한 명만 남으면 그 한 명의 도착으로 완료·보상이 성립했다.
     */
    public static final int MINIMUM_ARRIVED_MEMBERS = 2;

    /** 도착자가 부족해 만남이 성립하지 않은 그룹의 취소 사유. */
    public static final String INSUFFICIENT_ARRIVALS = "INSUFFICIENT_ARRIVALS";

    private MatchMeetingWindowPolicy() {
    }

    /** 만남이 끝났다고 보는 시각. 이 시각부터 그룹을 완료 또는 취소로 닫는다. */
    public static OffsetDateTime closesAt(OffsetDateTime confirmedAt) {
        return confirmedAt.plus(MEETING_WINDOW);
    }

    /** 도착 인원이 만남 성립 기준을 채웠는지. */
    public static boolean isMeetingHeld(int arrivedCount) {
        return arrivedCount >= MINIMUM_ARRIVED_MEMBERS;
    }
}
