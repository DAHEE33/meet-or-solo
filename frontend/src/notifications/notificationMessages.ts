import type { MatchingStateChangedNotification } from '../api/matchingWebSocket';

/**
 * 서버가 보내는 상태 변화 사유를 사람이 읽는 알림으로 바꾼다.
 *
 * <p>서버는 `MatchingStateChangedEvent`의 `reason` 문자열만 보낸다. 화면 이동 경로와 문구는
 * 프론트가 정한다. 사유 목록은 백엔드의 `MatchProposalResponseService.notificationReason`,
 * `MatchArrivalService`, `MatchCancellationService`, `MatchNoShowGroupService`,
 * `MatchLeaveService`, `MatchMeetingCloseGroupService`에서 나온다.
 */

export type NotificationLevel =
  /** 지금 손을 써야 하는 알림. 배너로 띄우고 저절로 사라지지 않는다. */
  | 'URGENT'
  /** 알아두면 되는 알림. 토스트로 잠깐 띄운다. */
  | 'INFO';

export type NotificationMessage = {
  title: string;
  body?: string;
  /** 알림을 눌렀을 때 갈 곳. */
  path: string;
  level: NotificationLevel;
};

const MESSAGES: Record<string, NotificationMessage> = {
  /**
   * 응답 시간이 30초다. 놓치면 `penalty_score +1`과 쿨타임 2분이 붙으므로(백엔드
   * `MatchingPenaltyPolicy.roundOneTimeout`) 유일하게 배너로 띄운다.
   */
  MATCH_PROPOSED: {
    title: '매칭 상대를 찾았어요',
    body: '30초 안에 수락해야 매칭이 이어져요.',
    path: '/matching',
    level: 'URGENT',
  },
  MATCH_CONFIRMED: {
    title: '매칭이 확정됐어요',
    body: '만남 장소를 확인하고 도착 시간을 알려주세요.',
    path: '/match-room',
    level: 'URGENT',
  },
  MATCH_ACCEPTED: {
    title: '상대가 수락했어요',
    body: '남은 인원의 응답을 기다리고 있어요.',
    path: '/matching',
    level: 'INFO',
  },
  MATCH_REJECTED: {
    title: '이번 매칭은 성사되지 않았어요',
    body: '다시 신청하면 새 상대를 찾아요.',
    path: '/matching',
    level: 'INFO',
  },
  MATCH_TIMEOUT: {
    title: '응답 시간이 지나 매칭이 종료됐어요',
    path: '/matching',
    level: 'INFO',
  },
  MATCH_INSUFFICIENT_MEMBERS: {
    title: '인원이 모자라 매칭이 종료됐어요',
    path: '/matching',
    level: 'INFO',
  },
  ARRIVAL_TIME_SELECTED: {
    title: '상대가 도착 예정 시간을 알렸어요',
    path: '/match-room',
    level: 'INFO',
  },
  MEMBER_ARRIVED: {
    title: '상대가 만남 장소에 도착했어요',
    path: '/match-room',
    level: 'INFO',
  },
  ALL_ARRIVED: {
    title: '모두 도착했어요',
    body: '만남이 시작됐어요. 즐거운 시간 보내세요.',
    path: '/match-room',
    level: 'INFO',
  },
  MEMBER_CANCELLED: {
    title: '한 명이 참여를 취소했어요',
    path: '/match-room',
    level: 'INFO',
  },
  MEMBER_LEFT: {
    title: '한 명이 먼저 갔어요',
    path: '/match-room',
    level: 'INFO',
  },
  MEMBER_NO_SHOW: {
    title: '도착 마감까지 오지 않은 멤버가 있어요',
    path: '/match-room',
    level: 'INFO',
  },
  MATCH_CANCELLED: {
    title: '만남이 종료됐어요',
    body: '남은 인원으로는 만남을 이어갈 수 없었어요.',
    path: '/matching',
    level: 'INFO',
  },
  MATCH_COMPLETED: {
    title: '만남이 끝났어요',
    body: '매너온도가 올랐어요. 마이페이지에서 확인해보세요.',
    path: '/mypage',
    level: 'INFO',
  },
  /** 매칭 실패로 상태방이 아니라 대기 화면이 정리되는 경우다. */
  CANCELLED: {
    title: '매칭이 취소됐어요',
    path: '/matching',
    level: 'INFO',
  },
};

/**
 * 모르는 사유도 버리지 않는다.
 *
 * <p>백엔드에 사유가 하나 늘었을 때 알림이 통째로 사라지면 원인을 찾기 어렵다. 문구는 밋밋해도
 * 목록에는 남겨서 "뭔가 바뀌었다"를 알린다.
 */
const FALLBACK: NotificationMessage = {
  title: '매칭 상태가 바뀌었어요',
  path: '/matching',
  level: 'INFO',
};

export function toNotificationMessage(
  notification: Pick<MatchingStateChangedNotification, 'reason'>,
): NotificationMessage {
  return MESSAGES[notification.reason] ?? FALLBACK;
}

/** 알림을 이미 보고 있는 화면이면 토스트를 띄우지 않는다. */
export function isAlreadyVisible(message: NotificationMessage, pathname: string): boolean {
  if (message.level === 'URGENT') return false;
  return pathname === message.path;
}
