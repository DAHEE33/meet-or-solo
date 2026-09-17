import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { NotificationList } from '../api/notifications';

const list = vi.fn();
const markAllReadApi = vi.fn();

vi.mock('../api/notifications', () => ({
  notificationsApi: {
    list: (...args: unknown[]) => list(...args),
    markAllRead: (...args: unknown[]) => markAllReadApi(...args),
  },
}));

const {
  __resetNotificationsForTest,
  addNotification,
  applyServerNotifications,
  getNotificationState,
  markAllReadEverywhere,
  syncNotifications,
} = await import('./notificationStore');

const serverList = (items: NotificationList['items'], unreadCount: number): NotificationList => ({
  items,
  unreadCount,
  retentionDays: 30,
  retentionCount: 100,
});

/**
 * 2단계 알림함(docs/32 3.3). 1단계는 목록이 기기 안에만 있어서 로그인해도 못 본 알림이
 * 복원되지 않았다.
 */
describe('서버 알림함', () => {
  beforeEach(() => {
    __resetNotificationsForTest();
    list.mockReset();
    markAllReadApi.mockReset();
  });

  it('로그인하면 서버 목록과 읽지 않음 수를 받아온다', async () => {
    list.mockResolvedValue(serverList([
      { notificationId: 2, reason: 'MATCH_COMPLETED', occurredAt: '2026-09-14T13:00:00+09:00', read: false },
      { notificationId: 1, reason: 'MATCH_PROPOSED', occurredAt: '2026-09-14T12:00:00+09:00', read: true },
    ], 1));

    await syncNotifications();

    const state = getNotificationState();
    expect(state.items.map((item) => item.reason)).toEqual(['MATCH_COMPLETED', 'MATCH_PROPOSED']);
    expect(state.unreadCount).toBe(1);
    expect(state.retention).toEqual({ days: 30, count: 100 });
  });

  /** 저장하지 않는 중간 상태 알림은 이번 세션에서만 보이고 서버 목록에 섞여도 중복되지 않는다. */
  it('세션 알림과 서버 알림을 시간순으로 합치고 같은 알림은 한 번만 남긴다', () => {
    addNotification({
      type: 'MATCHING_STATE_CHANGED',
      reason: 'MEMBER_ARRIVED',
      occurredAt: '2026-09-14T12:30:00+09:00',
    });
    addNotification({
      type: 'MATCHING_STATE_CHANGED',
      reason: 'MATCH_CONFIRMED',
      occurredAt: '2026-09-14T12:00:00+09:00',
    });

    applyServerNotifications(serverList([
      { notificationId: 5, reason: 'MATCH_CONFIRMED', occurredAt: '2026-09-14T12:00:00+09:00', read: true },
    ], 0));

    const state = getNotificationState();
    expect(state.items.map((item) => item.reason)).toEqual(['MEMBER_ARRIVED', 'MATCH_CONFIRMED']);
    // 서버가 가진 알림은 서버의 읽음 상태를 따른다.
    expect(state.items.find((item) => item.reason === 'MATCH_CONFIRMED')?.read).toBe(true);
    expect(state.unreadCount).toBe(1);
  });

  it('목록을 열면 화면을 먼저 읽음으로 바꾸고 서버에도 기록한다', async () => {
    addNotification({
      type: 'MATCHING_STATE_CHANGED',
      reason: 'MATCH_PROPOSED',
      occurredAt: '2026-09-14T12:00:00+09:00',
    });
    markAllReadApi.mockResolvedValue(serverList([
      { notificationId: 1, reason: 'MATCH_PROPOSED', occurredAt: '2026-09-14T12:00:00+09:00', read: true },
    ], 0));

    await markAllReadEverywhere();

    expect(markAllReadApi).toHaveBeenCalledOnce();
    expect(getNotificationState().unreadCount).toBe(0);
  });

  /**
   * mount 시점에 받아오면 로그인 화면에서 인증 없이 조회했다가 실패한 채로 끝난다.
   * 소켓이 붙었다는 것은 인증이 있다는 뜻이고, 재연결마다 다시 받아오면 끊겨 있는 동안 온
   * 알림이 채워진다.
   */
  it('소켓이 붙을 때마다 서버 알림함을 다시 받아온다', async () => {
    list.mockResolvedValue(serverList([
      { notificationId: 1, reason: 'MATCH_PROPOSED', occurredAt: '2026-09-14T12:00:00+09:00', read: false },
    ], 1));

    await syncNotifications();
    await syncNotifications();

    expect(list).toHaveBeenCalledTimes(2);
    // 두 번 받아도 같은 알림이 두 줄이 되지 않는다.
    expect(getNotificationState().items).toHaveLength(1);
    expect(getNotificationState().unreadCount).toBe(1);
  });

  /** 로그인 전이거나 네트워크가 끊긴 상태에서도 실시간 알림은 그대로 동작해야 한다. */
  it('서버 목록을 못 받아도 세션 알림은 유지된다', async () => {
    addNotification({
      type: 'MATCHING_STATE_CHANGED',
      reason: 'MATCH_PROPOSED',
      occurredAt: '2026-09-14T12:00:00+09:00',
    });
    list.mockRejectedValue(new Error('unauthorized'));

    await syncNotifications();

    expect(getNotificationState().items).toHaveLength(1);
    expect(getNotificationState().unreadCount).toBe(1);
  });

  it('읽음 기록이 실패해도 화면은 읽음으로 남는다', async () => {
    addNotification({
      type: 'MATCHING_STATE_CHANGED',
      reason: 'MATCH_PROPOSED',
      occurredAt: '2026-09-14T12:00:00+09:00',
    });
    markAllReadApi.mockRejectedValue(new Error('network'));

    await markAllReadEverywhere();

    expect(getNotificationState().unreadCount).toBe(0);
  });
});
