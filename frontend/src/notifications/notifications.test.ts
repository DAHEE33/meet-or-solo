import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  __setConnectImplForTest,
  openSocketCount,
  subscribeMatchingNotifications,
} from '../api/matchingNotificationHub';
import type { MatchingWebSocketCallbacks } from '../api/matchingWebSocket';
import { NOTICE_DISMISS_MS } from '../components/common/TopNotice';
import {
  NOTICE_VISIBLE_MS,
  isAlreadyVisible,
  toNotificationMessage,
} from './notificationMessages';
import {
  MAX_NOTIFICATIONS,
  __resetNotificationsForTest,
  addNotification,
  getNotificationState,
  markAllRead,
} from './notificationStore';
import { shouldConnect } from '../components/notifications/NotificationCenter';

function fakeConnect() {
  let callbacks: MatchingWebSocketCallbacks | null = null;
  let opened = 0;
  let closed = 0;
  const impl = (next: MatchingWebSocketCallbacks) => {
    opened += 1;
    callbacks = next;
    return () => {
      closed += 1;
      callbacks = null;
    };
  };
  return {
    impl,
    counts: () => ({ opened, closed }),
    emit: (reason: string, occurredAt = '2026-09-14T12:00:00+09:00') =>
      callbacks?.onStateChanged({
        type: 'MATCHING_STATE_CHANGED',
        reason,
        occurredAt,
      }),
    connect: () => callbacks?.onConnected(),
  };
}

describe('matchingNotificationHub', () => {
  beforeEach(() => {
    __setConnectImplForTest(null);
  });

  /**
   * 이 테스트가 이 작업의 핵심 회귀 방어다. 전역 알림을 넣으면서 연결을 하나 더 만들면
   * 매칭 화면에서 소켓이 2개가 되고 같은 알림을 두 번 받는다.
   */
  it('구독자가 여럿이어도 소켓은 하나만 연다', () => {
    const fake = fakeConnect();
    __setConnectImplForTest(fake.impl);
    const received: string[] = [];

    const first = subscribeMatchingNotifications({
      onConnected: () => {},
      onStateChanged: (notification) => received.push(`a:${notification.reason}`),
    });
    const second = subscribeMatchingNotifications({
      onConnected: () => {},
      onStateChanged: (notification) => received.push(`b:${notification.reason}`),
    });

    expect(openSocketCount()).toBe(1);
    expect(fake.counts().opened).toBe(1);

    fake.emit('MATCH_CONFIRMED');
    expect(received).toEqual(['a:MATCH_CONFIRMED', 'b:MATCH_CONFIRMED']);

    first();
    // 아직 구독자가 남아 있으므로 끊지 않는다.
    expect(openSocketCount()).toBe(1);
    second();
    expect(openSocketCount()).toBe(0);
    expect(fake.counts().closed).toBe(1);
  });

  /** 연결이 끝난 뒤 구독한 쪽은 첫 동기화 신호를 받지 못해 폴링만 돌게 된다. */
  it('이미 연결된 뒤 구독하면 onConnected를 즉시 받는다', () => {
    const fake = fakeConnect();
    __setConnectImplForTest(fake.impl);
    subscribeMatchingNotifications({ onConnected: () => {}, onStateChanged: () => {} });
    fake.connect();

    const onConnected = vi.fn();
    subscribeMatchingNotifications({ onConnected, onStateChanged: () => {} });

    expect(onConnected).toHaveBeenCalledTimes(1);
  });

  it('같은 구독을 두 번 해제해도 다른 구독자의 소켓을 닫지 않는다', () => {
    const fake = fakeConnect();
    __setConnectImplForTest(fake.impl);
    const unsubscribe = subscribeMatchingNotifications({
      onConnected: () => {}, onStateChanged: () => {},
    });
    subscribeMatchingNotifications({ onConnected: () => {}, onStateChanged: () => {} });

    unsubscribe();
    unsubscribe();

    expect(openSocketCount()).toBe(1);
  });
});

describe('notificationMessages', () => {
  /** 응답 30초라 놓치면 penalty_score +1과 쿨타임 2분이 붙는다. 유일한 URGENT다. */
  it('매칭 제안은 배너로 띄우는 URGENT다', () => {
    const message = toNotificationMessage({ reason: 'MATCH_PROPOSED' });
    expect(message.level).toBe('URGENT');
    expect(message.path).toBe('/matching');
    expect(message.body).toContain('30초');
  });

  it('만남 완료는 매너온도를 안내하고 마이페이지로 보낸다', () => {
    const message = toNotificationMessage({ reason: 'MATCH_COMPLETED' });
    expect(message.path).toBe('/mypage');
    expect(message.body).toContain('매너온도');
  });

  /** 백엔드에 사유가 늘었을 때 알림이 통째로 사라지면 원인을 찾기 어렵다. */
  it('모르는 사유도 버리지 않고 기본 문구로 남긴다', () => {
    expect(toNotificationMessage({ reason: 'SOMETHING_NEW' }).title)
      .toBe('매칭 상태가 바뀌었어요');
  });

  it('이미 보고 있는 화면이면 알림을 띄우지 않는다', () => {
    const arrived = toNotificationMessage({ reason: 'MEMBER_ARRIVED' });
    expect(isAlreadyVisible(arrived, '/match-room')).toBe(true);
    expect(isAlreadyVisible(arrived, '/')).toBe(false);
  });

  /** 급한 알림은 어느 화면에 있든 띄운다. 놓치면 페널티가 붙기 때문이다. */
  it('URGENT는 같은 화면에 있어도 띄운다', () => {
    const proposed = toNotificationMessage({ reason: 'MATCH_PROPOSED' });
    expect(isAlreadyVisible(proposed, '/matching')).toBe(false);
  });

  /**
   * "알림이 안 사라진다"의 회귀 방어다.
   *
   * <p>예전에는 긴급 알림(배너)에 타이머가 아예 없어서 닫기를 누르기 전까지 남았다. 화면을
   * 옮겨도 따라다녔고, 응답 시간 30초가 지나 이미 끝난 제안의 배너가 그대로 떠 있었다.
   *
   * <p>긴급도 일반과 같은 5초다. 자리마다 3초·5초·무제한으로 달랐던 것을 하나로 모았다
   * ({@link NOTICE_DISMISS_MS}). 긴급 알림을 더 오래 띄우지 않는 이유는 놓쳐도 사라지지 않는
   * 경로가 이미 둘(매칭 화면의 제안 카드, 헤더의 종) 있기 때문이다.
   */
  it('모든 알림이 같은 시간 동안 뜨고 저절로 사라진다', () => {
    expect(NOTICE_VISIBLE_MS.URGENT).toBe(NOTICE_DISMISS_MS);
    expect(NOTICE_VISIBLE_MS.INFO).toBe(NOTICE_DISMISS_MS);
    expect(NOTICE_DISMISS_MS).toBe(5_000);
  });
});

describe('notificationStore', () => {
  // 저장소 접근은 store가 try/catch로 감싼다. 이 테스트 환경에는 window 자체가 없는데,
  // 그 상태에서도 알림이 동작해야 한다는 것까지 함께 확인하는 셈이다.
  beforeEach(() => {
    __resetNotificationsForTest();
  });

  it('받은 알림을 최신순으로 쌓고 읽지 않음을 센다', () => {
    addNotification({
      type: 'MATCHING_STATE_CHANGED', reason: 'MEMBER_ARRIVED',
      occurredAt: '2026-09-14T12:00:00+09:00',
    });
    addNotification({
      type: 'MATCHING_STATE_CHANGED', reason: 'MATCH_COMPLETED',
      occurredAt: '2026-09-14T13:00:00+09:00',
    });

    const state = getNotificationState();
    expect(state.items.map((item) => item.reason))
      .toEqual(['MATCH_COMPLETED', 'MEMBER_ARRIVED']);
    expect(state.unreadCount).toBe(2);
  });

  /** 재연결하면 같은 알림이 다시 온다. 그때마다 토스트가 뜨면 화면이 시끄러워진다. */
  it('같은 알림은 두 번 쌓지 않는다', () => {
    const notification = {
      type: 'MATCHING_STATE_CHANGED' as const, reason: 'MATCH_CONFIRMED',
      occurredAt: '2026-09-14T12:00:00+09:00',
    };
    expect(addNotification(notification)).not.toBeNull();
    expect(addNotification(notification)).toBeNull();
    expect(getNotificationState().items).toHaveLength(1);
  });

  it('목록을 열면 읽음으로 바뀌고 뱃지가 사라진다', () => {
    addNotification({
      type: 'MATCHING_STATE_CHANGED', reason: 'MATCH_CONFIRMED',
      occurredAt: '2026-09-14T12:00:00+09:00',
    });
    markAllRead();
    expect(getNotificationState().unreadCount).toBe(0);
  });

  it('보관 개수를 넘으면 오래된 알림부터 버린다', () => {
    for (let index = 0; index < MAX_NOTIFICATIONS + 5; index += 1) {
      addNotification({
        type: 'MATCHING_STATE_CHANGED', reason: 'MEMBER_ARRIVED',
        occurredAt: `2026-09-14T12:00:${String(index).padStart(2, '0')}+09:00`,
      });
    }
    expect(getNotificationState().items).toHaveLength(MAX_NOTIFICATIONS);
  });
});

describe('NotificationCenter 연결 조건', () => {
  /** 로그인 전에는 인증이 없어 연결이 5초마다 실패·재시도한다. */
  it('로그인 화면에서는 소켓을 열지 않는다', () => {
    expect(shouldConnect('/login')).toBe(false);
    expect(shouldConnect('/signup')).toBe(false);
    expect(shouldConnect('/')).toBe(true);
    expect(shouldConnect('/match-room')).toBe(true);
  });
});
