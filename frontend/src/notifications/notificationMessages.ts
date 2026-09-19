import { NOTICE_DISMISS_MS } from '../components/common/TopNotice';
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
  /** 지금 손을 써야 하는 알림. 눈에 띄는 색으로 오래 띄운다. */
  | 'URGENT'
  /** 알아두면 되는 알림. 잠깐 띄운다. */
  | 'INFO';

/**
 * 알림이 화면에 머무는 시간.
 *
 * <p><b>긴급 알림도 저절로 사라지고, 시간은 모든 알림이 같다.</b> 예전에는 배너에 타이머가
 * 없어 "닫기"를 누르기 전까지 영영 남았고, 화면을 옮겨도 따라다녔다. 자리마다 3초·5초·무제한
 * 으로 제각각이기도 했다.
 *
 * <p>긴급 알림을 더 오래 띄우지 않는다. 제안을 놓치면 안 된다는 이유로 응답 시간과 같은 30초를
 * 줬었는데, 화면을 30초 동안 가리는 값이라 부담이 컸다. 대신 <b>놓쳐도 사라지지 않는 경로가
 * 이미 둘 있다</b> — 매칭 화면의 제안 카드(카운트다운 포함)와 헤더의 종이다. 색으로만
 * 구분한다(긴급 코랄, 일반 잉크).
 */
export const NOTICE_VISIBLE_MS: Record<NotificationLevel, number> = {
  URGENT: NOTICE_DISMISS_MS,
  INFO: NOTICE_DISMISS_MS,
};

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
   * `MatchingPenaltyPolicy.roundOneTimeout`) 긴급으로 띄운다.
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
  /*
    아래 상태방 사유들은 `MatchRoomPage.matchEventText`와 같은 어휘를 쓴다. 같은 사건을 두
    화면이 다른 말로 부르면 사용자는 다른 일이 일어난 것으로 읽는다. 닉네임만 차이가 있다 —
    알림은 행위자를 모르므로 "상대가"로만 말할 수 있다.
  */
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
    title: '한 명이 도착 마감까지 오지 않았어요',
    path: '/match-room',
    level: 'INFO',
  },
  MATCH_CANCELLED: {
    title: '만남이 종료됐어요',
    body: '남은 인원으로 만남을 이어갈 수 없었어요.',
    path: '/matching',
    level: 'INFO',
  },
  MATCH_COMPLETED: {
    title: '만남이 끝났어요',
    body: '매너온도가 올랐어요. 마이페이지에서 확인해보세요.',
    path: '/mypage',
    level: 'INFO',
  },
};

/**
 * `ALL_ARRIVED`와 `MEMBER_LEFT`는 `fix/wbs-10-b-match-completion-and-report-scope`에서 생긴
 * 사유다. 그 브랜치가 병합되기 전에는 서버가 보내지 않지만, 매핑을 미리 두어도 해가 없고
 * 병합 순서에 따라 알림이 잠깐 비는 일을 막는다.
 */

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

/**
 * 알림이 가리키는 화면을 이미 보고 있는지.
 *
 * <p>두 자리에서 쓴다. 알림이 올 때는 <b>띄울지</b>를 정하고, 알림이 떠 있는 동안에는
 * <b>내릴지</b>를 정한다. 그 화면에 도착했으면 "가서 보라"는 신호는 역할을 다했다.
 *
 * <p>긴급 알림은 예외다. 제안을 놓치면 `penalty_score +1`과 쿨타임 2분이 붙어서, 매칭 화면에
 * 있더라도 눈에 띄게 알려야 한다. 대신 {@link NOTICE_VISIBLE_MS}가 지나면 사라진다.
 */
export function isAlreadyVisible(message: NotificationMessage, pathname: string): boolean {
  if (message.level === 'URGENT') return false;
  return pathname === message.path;
}
