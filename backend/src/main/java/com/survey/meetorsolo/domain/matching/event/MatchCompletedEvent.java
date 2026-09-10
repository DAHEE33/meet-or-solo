package com.survey.meetorsolo.domain.matching.event;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 만남이 전원 도착으로 완료됐을 때 발행한다({@code docs/19} 4.9).
 *
 * <p>기존 {@code MatchingStateChangedEvent}를 재사용하지 않는 이유는 그쪽이 WebSocket 상태
 * 동기화 전용이고 {@code groupId}를 담지 않기 때문이다. 매너온도 보상은 그룹당 1회이므로
 * {@code groupId}가 없으면 중복 지급을 막을 수 없다.
 *
 * @param memberIds 완료 처리된 활성 참여자. 취소·노쇼로 빠진 회원은 들어 있지 않다.
 */
public record MatchCompletedEvent(long groupId, List<Long> memberIds, OffsetDateTime completedAt) {
}
