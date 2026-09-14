import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  __setConnectImplForTest,
  openSocketCount,
  subscribeMatchingNotifications,
} from '../api/matchingNotificationHub';
import type { MatchingWebSocketCallbacks } from '../api/matchingWebSocket';
import AppHeader from '../components/layout/AppHeader';
import { isAlreadyVisible } from './notificationMessages';
import {
  __resetNotificationsForTest,
  addNotification,
  getNotificationState,
  markAllRead,
  type StoredNotification,
} from './notificationStore';

/**
 * 매칭 한 판을 처음부터 끝까지 흘려보내며 알림이 실제로 이어지는지 확인하는 하네스다.
 *
 * <p>조각 테스트(`notifications.test.ts`)와 목적이 다르다. 그쪽은 허브·store·문구 매핑을 하나씩
 * 고정하고, 여기서는 <b>서버 이벤트 → 허브 → store → 화면에 보이는 것</b>이 순서대로 이어지는지
 * 본다. 각 조각이 맞아도 사이가 끊기면 사용자에게는 아무 알림도 뜨지 않는다.
 *
 * <p>jsdom이 없으므로 DOM 이벤트 대신 <b>서버 렌더 마크업</b>으로 화면을 확인한다. 종의 뱃지처럼
 * 초기 렌더에 드러나는 것은 이것으로 충분하고, 클릭 이후 동작은 `docs/30` 수동 검증이 맡는다.
 */

type Emit = (reason: string, occurredAt: string) => void;

/** 실제 소켓 대신 이벤트를 손으로 흘려보낼 수 있는 연결을 만든다. */
function fakeSocket() {
  let callbacks: MatchingWebSocketCallbacks | null = null;
  const impl = (next: MatchingWebSocketCallbacks) => {
    callbacks = next;
    return () => { callbacks = null; };
  };
  const emit: Emit = (reason, occurredAt) => callbacks?.onStateChanged({
    type: 'MATCHING_STATE_CHANGED', reason, occurredAt,
  });
  return { impl, emit, drop: () => callbacks?.onDisconnected?.() };
}

/**
 * `NotificationCenter`가 하는 일을 그대로 옮긴 구독자다.
 *
 * <p>컴포넌트를 직접 마운트하려면 jsdom이 필요해서, 같은 판단(배너/토스트/생략)을 여기서
 * 재현한다. 판단 기준 자체는 `notificationMessages`의 함수를 그대로 쓴다.
 */
function startNotificationCenter(pathname: () => string) {
  const banners: StoredNotification[] = [];
  const toasts: StoredNotification[] = [];
  const stop = subscribeMatchingNotifications({
    onConnected: () => {},
    onStateChanged: (notification) => {
      const added = addNotification(notification);
      if (!added) return;
      if (added.message.level === 'URGENT') {
        banners.push(added);
        return;
      }
      if (isAlreadyVisible(added.message, pathname())) return;
      toasts.push(added);
    },
  });
  return { banners, toasts, stop };
}

const header = () => renderToStaticMarkup(
  <MemoryRouter><AppHeader /></MemoryRouter>,
);

describe('매칭 한 판의 알림 흐름', () => {
  beforeEach(() => {
    __setConnectImplForTest(null);
    __resetNotificationsForTest();
  });

  /**
   * 홈에 머무는 동안 매칭이 성사되는 흐름이다. 이 기능을 만든 이유가 정확히 이 상황이다.
   * 예전에는 `/matching`과 `/match-room`만 구독해서 홈에서는 아무것도 오지 않았다.
   */
  it('홈에 있어도 제안부터 완료까지 알림이 이어지고 종에 쌓인다', () => {
    const socket = fakeSocket();
    __setConnectImplForTest(socket.impl);
    const center = startNotificationCenter(() => '/');

    socket.emit('MATCH_PROPOSED', '2026-09-14T12:00:00+09:00');
    socket.emit('MATCH_CONFIRMED', '2026-09-14T12:00:20+09:00');
    socket.emit('MEMBER_ARRIVED', '2026-09-14T12:10:00+09:00');
    socket.emit('ALL_ARRIVED', '2026-09-14T12:12:00+09:00');
    socket.emit('MATCH_COMPLETED', '2026-09-14T13:00:20+09:00');

    // 응답 30초짜리 제안과 확정은 저절로 사라지면 안 되므로 배너다.
    expect(center.banners.map((item) => item.reason))
      .toEqual(['MATCH_PROPOSED', 'MATCH_CONFIRMED']);
    expect(center.toasts.map((item) => item.reason))
      .toEqual(['MEMBER_ARRIVED', 'ALL_ARRIVED', 'MATCH_COMPLETED']);

    const state = getNotificationState();
    expect(state.items).toHaveLength(5);
    expect(state.unreadCount).toBe(5);
    // 종에 읽지 않음 수가 보인다.
    expect(header()).toContain('알림 5건');

    markAllRead();
    expect(header()).toContain('aria-label="알림"');
    center.stop();
  });

  /** 상태방을 보고 있으면 화면이 이미 갱신된다. 같은 사실을 토스트로 또 알릴 이유가 없다. */
  it('상태방에 있으면 그 방 알림은 토스트로 중복되지 않는다', () => {
    const socket = fakeSocket();
    __setConnectImplForTest(socket.impl);
    const center = startNotificationCenter(() => '/match-room');

    socket.emit('MEMBER_ARRIVED', '2026-09-14T12:10:00+09:00');
    socket.emit('MEMBER_LEFT', '2026-09-14T12:40:00+09:00');

    expect(center.toasts).toHaveLength(0);
    // 띄우지 않았을 뿐 기록은 남는다. 나중에 종으로 확인할 수 있어야 한다.
    expect(getNotificationState().items).toHaveLength(2);
    center.stop();
  });

  /** 제안은 놓치면 penalty_score +1과 쿨타임 2분이 붙는다. 어느 화면에 있든 띄워야 한다. */
  it('매칭 화면에 있어도 제안 배너는 뜬다', () => {
    const socket = fakeSocket();
    __setConnectImplForTest(socket.impl);
    const center = startNotificationCenter(() => '/matching');

    socket.emit('MATCH_PROPOSED', '2026-09-14T12:00:00+09:00');

    expect(center.banners).toHaveLength(1);
    center.stop();
  });

  /**
   * 끊겼다 붙으면 서버가 같은 알림을 다시 보낼 수 있다. 그때마다 배너가 뜨면 화면이
   * 시끄럽고, 목록에도 같은 줄이 쌓인다.
   */
  it('재연결로 같은 알림이 다시 와도 한 번만 쌓인다', () => {
    const socket = fakeSocket();
    __setConnectImplForTest(socket.impl);
    const center = startNotificationCenter(() => '/');

    socket.emit('MATCH_CONFIRMED', '2026-09-14T12:00:20+09:00');
    socket.drop();
    socket.emit('MATCH_CONFIRMED', '2026-09-14T12:00:20+09:00');

    expect(center.banners).toHaveLength(1);
    expect(getNotificationState().items).toHaveLength(1);
    center.stop();
  });

  /**
   * 알림 센터와 매칭 화면이 동시에 구독하는 상황이다. 소켓이 둘로 갈라지면 같은 알림을
   * 두 번 받고 배너가 겹친다.
   */
  it('매칭 화면이 함께 구독해도 소켓은 하나고 알림 기록은 한 벌이다', () => {
    const socket = fakeSocket();
    __setConnectImplForTest(socket.impl);
    const center = startNotificationCenter(() => '/match-room');
    const screenRefreshes: string[] = [];
    const screen = subscribeMatchingNotifications({
      onConnected: () => {},
      onStateChanged: (notification) => screenRefreshes.push(notification.reason),
    });

    expect(openSocketCount()).toBe(1);
    socket.emit('MEMBER_ARRIVED', '2026-09-14T12:10:00+09:00');

    // 화면은 갱신 신호를 받고, 알림 기록은 한 번만 남는다.
    expect(screenRefreshes).toEqual(['MEMBER_ARRIVED']);
    expect(getNotificationState().items).toHaveLength(1);

    screen();
    center.stop();
    expect(openSocketCount()).toBe(0);
  });

  /** 알림이 없을 때 목록을 열면 안내가 보여야 한다. 빈 화면은 고장처럼 보인다. */
  it('받은 알림이 없으면 종에 뱃지가 없다', () => {
    const markup = header();
    expect(markup).toContain('aria-label="알림"');
    expect(markup).not.toContain('알림 1건');
  });
});
